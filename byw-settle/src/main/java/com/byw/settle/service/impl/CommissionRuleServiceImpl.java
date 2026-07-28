package com.byw.settle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.api.settle.dto.CommissionRuleDTO;
import com.byw.settle.entity.CommissionRule;
import com.byw.settle.mapper.CommissionRuleMapper;
import com.byw.settle.service.CommissionRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommissionRuleServiceImpl implements CommissionRuleService {

    private final CommissionRuleMapper commissionRuleMapper;

    @Override
    public List<CommissionRuleDTO> listRules() {
        List<CommissionRule> rules = commissionRuleMapper.selectList(new LambdaQueryWrapper<CommissionRule>()
                .orderByAsc(CommissionRule::getCategoryId));
        return rules.stream().map(r -> {
            CommissionRuleDTO dto = new CommissionRuleDTO();
            BeanUtils.copyProperties(r, dto);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public boolean saveRule(CommissionRuleDTO dto) {
        CommissionRule rule = new CommissionRule();
        BeanUtils.copyProperties(dto, rule);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        if (dto.getId() != null) {
            return commissionRuleMapper.updateById(rule) > 0;
        }
        return commissionRuleMapper.insert(rule) > 0;
    }

    @Override
    public boolean deleteRule(Long id) {
        return commissionRuleMapper.deleteById(id) > 0;
    }
}
