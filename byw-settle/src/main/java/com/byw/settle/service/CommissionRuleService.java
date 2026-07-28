package com.byw.settle.service;

import com.byw.api.settle.dto.CommissionRuleDTO;

import java.util.List;

/**
 * 佣金规则管理服务（平台侧）。
 */
public interface CommissionRuleService {

    List<CommissionRuleDTO> listRules();

    /** 新增或更新佣金规则（按 id 判断；categoryId 唯一） */
    boolean saveRule(CommissionRuleDTO dto);

    boolean deleteRule(Long id);
}
