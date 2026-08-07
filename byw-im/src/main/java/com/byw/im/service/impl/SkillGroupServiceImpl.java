package com.byw.im.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.im.entity.SkillGroup;
import com.byw.im.entity.SkillGroupStaff;
import com.byw.im.mapper.SkillGroupMapper;
import com.byw.im.mapper.SkillGroupStaffMapper;
import com.byw.im.service.SkillGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SkillGroupServiceImpl implements SkillGroupService {

    private final SkillGroupMapper skillGroupMapper;
    private final SkillGroupStaffMapper skillGroupStaffMapper;

    @Override
    public List<SkillGroup> listByShop(Long shopId) {
        return skillGroupMapper.selectList(
                new LambdaQueryWrapper<SkillGroup>()
                        .eq(SkillGroup::getShopId, shopId)
                        .eq(SkillGroup::getStatus, 1)
                        .orderByAsc(SkillGroup::getSort));
    }

    @Override
    public SkillGroup create(SkillGroup group) {
        skillGroupMapper.insert(group);
        return group;
    }

    @Override
    public SkillGroup update(SkillGroup group) {
        skillGroupMapper.updateById(group);
        return group;
    }

    @Override
    public void delete(Long id, Long shopId) {
        // 逻辑删除：同时删除关联记录
        skillGroupMapper.deleteById(id);
        skillGroupStaffMapper.delete(new LambdaQueryWrapper<SkillGroupStaff>()
                .eq(SkillGroupStaff::getGroupId, id));
    }

    @Override
    public List<Long> getStaffGroupIds(Long staffId) {
        return skillGroupStaffMapper.selectList(
                new LambdaQueryWrapper<SkillGroupStaff>()
                        .eq(SkillGroupStaff::getStaffId, staffId))
                .stream()
                .map(SkillGroupStaff::getGroupId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveStaffGroups(Long staffId, List<Long> groupIds) {
        // 先删
        skillGroupStaffMapper.delete(
                new LambdaQueryWrapper<SkillGroupStaff>().eq(SkillGroupStaff::getStaffId, staffId));
        // 后插
        if (groupIds != null && !groupIds.isEmpty()) {
            for (Long gid : groupIds) {
                SkillGroupStaff sgs = new SkillGroupStaff();
                sgs.setGroupId(gid);
                sgs.setStaffId(staffId);
                skillGroupStaffMapper.insert(sgs);
            }
        }
    }

    @Override
    public Long resolveGroup(String content, String intent, Long shopId) {
        List<SkillGroup> groups = listByShop(shopId);
        if (groups.isEmpty()) return null;

        // 1. 入口意图匹配：product→售前（关键词含"商品"的组），order→售后（关键词含"订单"的组）
        Long intentGroup = null;
        if (intent != null) {
            for (SkillGroup g : groups) {
                if (g.getKeywords() == null) continue;
                String kw = g.getKeywords().toLowerCase();
                if ("product".equals(intent) && kw.contains("商品")) {
                    intentGroup = g.getId();
                    break;
                }
                if ("order".equals(intent) && kw.contains("订单")) {
                    intentGroup = g.getId();
                    break;
                }
            }
        }

        // 2. 关键词匹配：用户消息首句命中技能组 keywords
        Long keywordGroup = null;
        if (content != null && !content.isBlank()) {
            String lower = content.toLowerCase();
            for (SkillGroup g : groups) {
                if (g.getKeywords() == null) continue;
                String[] keywords = g.getKeywords().split(",");
                for (String kw : keywords) {
                    if (kw.isBlank()) continue;
                    if (lower.contains(kw.trim().toLowerCase())) {
                        keywordGroup = g.getId();
                        break;
                    }
                }
                if (keywordGroup != null) break;
            }
        }

        // 优先级：关键词 > 入口意图 > null
        Long result = keywordGroup != null ? keywordGroup : intentGroup;
        log.info("IM 技能组路由：shopId={}, intent={}, content={}, 匹配组={}", shopId, intent, content, result);
        return result;
    }

    @Override
    public Set<Long> getGroupStaffIds(Long groupId) {
        return skillGroupStaffMapper.selectList(
                new LambdaQueryWrapper<SkillGroupStaff>()
                        .eq(SkillGroupStaff::getGroupId, groupId))
                .stream()
                .map(SkillGroupStaff::getStaffId)
                .collect(Collectors.toSet());
    }
}