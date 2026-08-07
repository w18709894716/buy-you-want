package com.byw.im.service;

import com.byw.im.dto.FaqOptionDTO;
import com.byw.im.entity.Faq;

import java.util.List;

/**
 * IM FAQ 知识库服务
 */
public interface FaqService {

    /** 获取店铺的FAQ列表（按sort升序） */
    List<Faq> listByShop(Long shopId);

    /** C 端引导选项：启用状态的 id+question（按sort升序） */
    List<FaqOptionDTO> listOptions(Long shopId);

    /** 创建FAQ */
    Faq create(Faq faq);

    /** 更新FAQ */
    Faq update(Faq faq);

    /** 删除FAQ */
    void delete(Long id, Long shopId);

    /** 匹配FAQ：用户消息匹配到启用的FAQ时返回答案，否则返回null */
    String matchFaq(Long shopId, String userMessage);
}