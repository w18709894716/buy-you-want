package com.byw.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.rocketmq.constant.RocketMQTopics;
import com.byw.common.redis.util.RedisUtil;
import com.byw.promotion.entity.SeckillActivity;
import com.byw.promotion.entity.SeckillOrder;
import com.byw.promotion.mapper.SeckillActivityMapper;
import com.byw.promotion.mapper.SeckillOrderMapper;
import com.byw.promotion.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final RedisUtil redisUtil;
    private final RocketMQTemplate rocketMQTemplate;

    private static final String SECKILL_STOCK_KEY = "seckill:stock:";
    private static final String SECKILL_USER_KEY = "seckill:user:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createActivity(SeckillActivity activity) {
        activity.setStatus(0); // 未开始
        activity.setAvailableStock(activity.getTotalStock());
        seckillActivityMapper.insert(activity);

        // 初始化Redis库存
        String stockKey = SECKILL_STOCK_KEY + activity.getId();
        redisUtil.set(stockKey, activity.getTotalStock());

        log.info("秒杀活动创建成功: id={}, name={}", activity.getId(), activity.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void seckill(Long activityId, Long userId) {
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

        // 3. Redis检查用户是否已参与（每人限购一次）
        String userKey = SECKILL_USER_KEY + activityId + ":" + userId;
        if (Boolean.TRUE.equals(redisUtil.hasKey(userKey))) {
            throw new BusinessException("您已参与过该秒杀活动");
        }

        // 4. Redis扣减库存
        String stockKey = SECKILL_STOCK_KEY + activityId;
        Long stock = redisUtil.increment(stockKey, -1);
        if (stock < 0) {
            // 库存不足，回退
            redisUtil.increment(stockKey, 1);
            throw new BusinessException("库存不足");
        }

        // 5. 标记用户已参与
        redisUtil.set(userKey, "1");

        // 6. 创建秒杀订单记录
        SeckillOrder seckillOrder = new SeckillOrder();
        seckillOrder.setActivityId(activityId);
        seckillOrder.setUserId(userId);
        seckillOrder.setStatus(0); // 待支付
        seckillOrderMapper.insert(seckillOrder);

        // 7. 更新数据库库存
        activity.setAvailableStock(activity.getAvailableStock() - 1);
        seckillActivityMapper.updateById(activity);

        // 8. 发送RocketMQ消息异步创建订单
        Map<String, Object> event = Map.of(
                "activityId", activityId,
                "userId", userId,
                "productId", activity.getProductId(),
                "skuId", activity.getSkuId(),
                "seckillPrice", activity.getSeckillPrice().toPlainString(),
                "timestamp", System.currentTimeMillis()
        );
        rocketMQTemplate.syncSendOrderly(RocketMQTopics.SECKILL_ORDER, event, String.valueOf(activityId));

        log.info("秒杀成功: activityId={}, userId={}", activityId, userId);
    }

    @Override
    public List<SeckillActivity> getSeckillList() {
        return seckillActivityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1)
                        .le(SeckillActivity::getStartTime, LocalDateTime.now())
                        .ge(SeckillActivity::getEndTime, LocalDateTime.now()));
    }
}
