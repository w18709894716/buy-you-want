package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.im.entity.Conversation;
import com.byw.im.entity.Satisfaction;
import com.byw.im.mapper.SatisfactionMapper;
import com.byw.im.service.SatisfactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class SatisfactionServiceImpl extends ServiceImpl<SatisfactionMapper, Satisfaction> implements SatisfactionService {

    private final SatisfactionMapper satisfactionMapper;

    @Override
    public Satisfaction submit(Satisfaction satisfaction) {
        // 校验评分范围
        if (satisfaction.getRating() == null || satisfaction.getRating() < 1 || satisfaction.getRating() > 5) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }
        // 幂等插入：conversationId 唯一约束，重复提交返回已存在的记录
        try {
            save(satisfaction);
            log.info("IM 满意度评价提交：conversationId={}, userId={}, rating={}", 
                    satisfaction.getConversationId(), satisfaction.getUserId(), satisfaction.getRating());
            return satisfaction;
        } catch (Exception e) {
            // 唯一约束冲突：已评价过，返回已有记录
            return getOne(new LambdaQueryWrapper<Satisfaction>()
                    .eq(Satisfaction::getConversationId, satisfaction.getConversationId()));
        }
    }

    @Override
    public boolean hasRated(Long conversationId, Long userId) {
        return count(new LambdaQueryWrapper<Satisfaction>()
                .eq(Satisfaction::getConversationId, conversationId)
                .eq(Satisfaction::getUserId, userId)) > 0;
    }

    @Override
    public IPage<Satisfaction> listByShop(Long shopId, Integer page, Integer pageSize) {
        return page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<Satisfaction>()
                        .eq(Satisfaction::getShopId, shopId)
                        .orderByDesc(Satisfaction::getCreatedAt));
    }

    @Override
    public SatisfactionStats stats(Long shopId) {
        long total = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId));
        if (total == 0) {
            return new SatisfactionStats(0.0, 0, 0, 0, 0, 0, 0);
        }
        long r5 = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId).eq(Satisfaction::getRating, 5));
        long r4 = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId).eq(Satisfaction::getRating, 4));
        long r3 = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId).eq(Satisfaction::getRating, 3));
        long r2 = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId).eq(Satisfaction::getRating, 2));
        long r1 = count(new LambdaQueryWrapper<Satisfaction>().eq(Satisfaction::getShopId, shopId).eq(Satisfaction::getRating, 1));
        double avg = (double) (5 * r5 + 4 * r4 + 3 * r3 + 2 * r2 + 1 * r1) / total;
        return new SatisfactionStats(avg, total, r5, r4, r3, r2, r1);
    }
}