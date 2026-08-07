package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.byw.im.dto.FaqOptionDTO;
import com.byw.im.entity.Faq;
import com.byw.im.mapper.FaqMapper;
import com.byw.im.service.FaqService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FAQ 知识库服务实现
 *
 * <h3>匹配策略</h3>
 * <ol>
 *   <li>按 sort 升序遍历店铺启用的 FAQ</li>
 *   <li>用户消息包含 FAQ 问题关键词，或 FAQ 问题包含用户消息关键词，即命中</li>
 *   <li>命中第一个即返回，不再继续匹配</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqServiceImpl implements FaqService {

    private final FaqMapper faqMapper;

    @Override
    public List<Faq> listByShop(Long shopId) {
        LambdaQueryWrapper<Faq> qw = Wrappers.lambdaQuery();
        qw.eq(Faq::getShopId, shopId);
        qw.orderByAsc(Faq::getSort);
        qw.orderByDesc(Faq::getCreatedAt);
        return faqMapper.selectList(qw);
    }

    @Override
    public List<FaqOptionDTO> listOptions(Long shopId) {
        LambdaQueryWrapper<Faq> qw = Wrappers.lambdaQuery();
        qw.eq(Faq::getShopId, shopId);
        qw.eq(Faq::getStatus, 1);
        qw.orderByAsc(Faq::getSort);
        return faqMapper.selectList(qw).stream().map(f -> {
            FaqOptionDTO dto = new FaqOptionDTO();
            dto.setId(f.getId());
            dto.setQuestion(f.getQuestion());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public Faq create(Faq faq) {
        if (faq.getSort() == null) faq.setSort(0);
        if (faq.getStatus() == null) faq.setStatus(1);
        faqMapper.insert(faq);
        return faq;
    }

    @Override
    public Faq update(Faq faq) {
        faqMapper.updateById(faq);
        return faq;
    }

    @Override
    public void delete(Long id, Long shopId) {
        LambdaQueryWrapper<Faq> qw = Wrappers.lambdaQuery();
        qw.eq(Faq::getId, id);
        qw.eq(Faq::getShopId, shopId);
        faqMapper.delete(qw);
    }

    @Override
    public String matchFaq(Long shopId, String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }
        List<Faq> list = faqMapper.selectList(Wrappers.<Faq>lambdaQuery()
                .eq(Faq::getShopId, shopId)
                .eq(Faq::getStatus, 1)
                .orderByAsc(Faq::getSort));
        String msg = userMessage.trim().toLowerCase();
        for (Faq faq : list) {
            String question = faq.getQuestion().trim().toLowerCase();
            // 双向包含匹配：用户消息包含FAQ问题，或FAQ问题包含用户消息
            if (msg.contains(question) || question.contains(msg)) {
                log.info("FAQ 匹配成功：faqId={}, question={}, userMessage={}", faq.getId(), faq.getQuestion(), userMessage);
                return faq.getAnswer();
            }
        }
        return null;
    }
}