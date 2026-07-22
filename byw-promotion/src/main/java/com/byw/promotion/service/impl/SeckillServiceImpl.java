package com.byw.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.common.redis.util.RedisUtil;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.entity.SeckillActivityItem;
import com.byw.promotion.entity.SeckillOrder;
import com.byw.promotion.mapper.SeckillActivityItemMapper;
import com.byw.promotion.mapper.SeckillActivityMapper;
import com.byw.promotion.mapper.SeckillOrderMapper;
import com.byw.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillActivityItemMapper seckillActivityItemMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final RedisUtil redisUtil;
    private final RocketMQTemplate rocketMQTemplate;

    /** item 维度的秒杀库存 key */
    private static final String SECKILL_STOCK_KEY = "seckill:stock:item:";
    /** item 维度的用户限购 key */
    private static final String SECKILL_USER_KEY = "seckill:user:item:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createActivity(SeckillActivity activity, List<SeckillActivityItem> items) {
        validateItems(items);
        activity.setStatus(0); // 未开始
        seckillActivityMapper.insert(activity);

        for (SeckillActivityItem item : items) {
            item.setId(null);
            item.setActivityId(activity.getId());
            item.setAvailableStock(item.getTotalStock());
            seckillActivityItemMapper.insert(item);
        }
        log.info("秒杀活动已入库: id={}, name={}, items={}", activity.getId(), activity.getName(), items.size());

        // Redis库存初始化失败不影响活动创建
        initRedisStock(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateActivity(SeckillActivity activity, List<SeckillActivityItem> items) {
        validateItems(items);
        SeckillActivity exist = seckillActivityMapper.selectById(activity.getId());
        if (exist == null) {
            throw new BusinessException("秒杀活动不存在");
        }
        seckillActivityMapper.updateById(activity);

        // 全量替换商品条目：逻辑删除旧条目并清理Redis，再插入新条目
        List<SeckillActivityItem> oldItems = getItemsByActivityId(activity.getId());
        for (SeckillActivityItem old : oldItems) {
            seckillActivityItemMapper.deleteById(old.getId());
            try {
                redisUtil.delete(SECKILL_STOCK_KEY + old.getId());
            } catch (Exception e) {
                log.warn("清理Redis库存失败: itemId={}", old.getId());
            }
        }
        for (SeckillActivityItem item : items) {
            item.setId(null);
            item.setActivityId(activity.getId());
            item.setAvailableStock(item.getTotalStock());
            seckillActivityItemMapper.insert(item);
        }
        log.info("秒杀活动已更新: id={}, items={}", activity.getId(), items.size());

        initRedisStock(items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteActivity(Long activityId) {
        seckillActivityMapper.deleteById(activityId);
        List<SeckillActivityItem> items = getItemsByActivityId(activityId);
        for (SeckillActivityItem item : items) {
            seckillActivityItemMapper.deleteById(item.getId());
            try {
                redisUtil.delete(SECKILL_STOCK_KEY + item.getId());
            } catch (Exception e) {
                log.warn("清理Redis库存失败: itemId={}", item.getId());
            }
        }
        log.info("秒杀活动已删除: id={}, items={}", activityId, items.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seckill(Long activityId, Long itemId, Long userId, Long addressId) {
        // 1. 查询秒杀活动
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException("秒杀活动不存在");
        }

        // 2. 检查活动状态
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException("秒杀活动未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException("秒杀活动已结束");
        }

        // 3. 查询活动商品条目
        SeckillActivityItem item = seckillActivityItemMapper.selectById(itemId);
        if (item == null || !activityId.equals(item.getActivityId())) {
            throw new BusinessException("秒杀商品不存在");
        }

        // 4. Redis检查用户是否已抢购过该商品（每人每商品限购一件）
        String userKey = SECKILL_USER_KEY + itemId + ":" + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(userKey))) {
            throw new BusinessException("您已抢购过该商品");
        }

        // 5. Redis扣减item库存
        String stockKey = SECKILL_STOCK_KEY + itemId;
        Long stock = redisUtil.increment(stockKey, -1);
        if (stock < 0) {
            // 库存不足，回退
            redisUtil.increment(stockKey, 1);
            throw new BusinessException("库存不足");
        }

        // 6. 标记用户已抢购
        redisUtil.set(userKey, "1");

        // 7. 创建秒杀订单记录
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setActivityId(activityId);
        seckillOrder.setItemId(itemId);
        seckillOrder.setUserId(userId);
        seckillOrder.setStatus(0); // 待支付
        seckillOrderMapper.insert(seckillOrder);

        // 8. 更新数据库item库存
        item.setAvailableStock(item.getAvailableStock() - 1);
        seckillActivityItemMapper.updateById(item);

        // 9. 发送RocketMQ消息异步创建订单
        Map<String, Object> event = new HashMap<>();
        event.put("activityId", activityId);
        event.put("itemId", itemId);
        event.put("userId", userId);
        event.put("productId", item.getProductId());
        event.put("skuId", item.getSkuId());
        event.put("seckillPrice", item.getSeckillPrice().toPlainString());
        event.put("timestamp", System.currentTimeMillis());
        // 用户指定的收货地址（为空则消费端回退默认地址）
        if (addressId != null) {
            event.put("addressId", addressId);
        }
        rocketMQTemplate.syncSendOrderly(RocketMQTopics.SECKILL_ORDER, event, String.valueOf(activityId));

        log.info("秒杀成功: activityId={}, itemId={}, userId={}", activityId, itemId, userId);
    }

    @Override
    public List<SeckillActivity> getSeckillList() {
        // 用户端：返回所有未结束的活动（status!=2 且 endTime 未过期）
        // 实际状态由前端根据 startTime/endTime 动态判断（即将开始/进行中/已结束）
        return seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .ne(SeckillActivity::getStatus, 2)
                        .ge(SeckillActivity::getEndTime, LocalDateTime.now())
                        .orderByAsc(SeckillActivity::getStartTime));
    }

    @Override
    public List<SeckillActivity> getAllSeckillList() {
        // 管理端：返回所有状态的活动，按创建时间倒序
        return seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .orderByDesc(SeckillActivity::getId));
    }

    @Override
    public List<SeckillActivityItem> getItemsByActivityId(Long activityId) {
        return seckillActivityItemMapper.selectList(
                new LambdaQueryWrapper<SeckillActivityItem>()
                        .eq(SeckillActivityItem::getActivityId, activityId)
                        .orderByAsc(SeckillActivityItem::getId));
    }

    /**
     * 校验商品条目：数量 1~50，SKU 不重复，价格/库存合法
     */
    private void validateItems(List<SeckillActivityItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少配置一个秒杀商品");
        }
        if (items.size() > MAX_ITEMS_PER_ACTIVITY) {
            throw new BusinessException("每个活动最多配置 " + MAX_ITEMS_PER_ACTIVITY + " 个商品");
        }
        Set<Long> skuIds = new HashSet<>();
        for (SeckillActivityItem item : items) {
            if (item.getProductId() == null || item.getSkuId() == null) {
                throw new BusinessException("商品条目必须选择商品和规格");
            }
            if (item.getSeckillPrice() == null || item.getSeckillPrice().signum() <= 0) {
                throw new BusinessException("秒杀价格必须大于0");
            }
            if (item.getTotalStock() == null || item.getTotalStock() <= 0) {
                throw new BusinessException("秒杀库存必须大于0");
            }
            if (!skuIds.add(item.getSkuId())) {
                throw new BusinessException("同一规格商品不能重复配置");
            }
        }
    }

    /**
     * 初始化各 item 的 Redis 库存（失败不影响DB事务）
     */
    private void initRedisStock(List<SeckillActivityItem> items) {
        for (SeckillActivityItem item : items) {
            try {
                String stockKey = SECKILL_STOCK_KEY + item.getId();
                redisUtil.set(stockKey, item.getTotalStock());
                log.info("Redis库存初始化成功: key={}, value={}", stockKey, item.getTotalStock());
            } catch (Exception e) {
                log.error("Redis库存初始化失败(不影响活动保存): itemId={}, error={}", item.getId(), e.getMessage());
            }
        }
    }
}

