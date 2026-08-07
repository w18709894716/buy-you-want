package com.byw.im.service;

import com.byw.im.entity.SkillGroup;

import java.util.List;
import java.util.Set;

/**
 * IM 技能组路由服务
 */
public interface SkillGroupService {

    /** 技能组列表（按 sort 升序） */
    List<SkillGroup> listByShop(Long shopId);

    /** 新增技能组 */
    SkillGroup create(SkillGroup group);

    /** 更新技能组 */
    SkillGroup update(SkillGroup group);

    /** 删除技能组 */
    void delete(Long id, Long shopId);

    /** 获取员工所属技能组ID列表 */
    List<Long> getStaffGroupIds(Long staffId);

    /** 保存员工技能组（先删后插） */
    void saveStaffGroups(Long staffId, List<Long> groupIds);

    /**
     * 根据用户首条消息内容 + 入口意图，匹配最佳技能组。
     * @param content 用户首条消息文本（可为空，图片/卡片无文本）
     * @param intent 入口意图（product/order/default，从C端入口传参）
     * @param shopId 店铺ID
     * @return 匹配的技能组ID，null 表示无匹配（全店兜底）
     */
    Long resolveGroup(String content, String intent, Long shopId);

    /** 获取技能组内所有启用的客服ID集合 */
    Set<Long> getGroupStaffIds(Long groupId);
}