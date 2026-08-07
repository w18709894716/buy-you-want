package com.byw.im.controller;

import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.im.entity.SkillGroup;
import com.byw.im.service.SkillGroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * IM 技能组管理 REST 接口（商家端）
 */
@Tag(name = "客服技能组")
@RestController
@RequestMapping("/im/skill-group")
@RequiredArgsConstructor
public class ImSkillGroupController {

    private final SkillGroupService skillGroupService;

    @Operation(summary = "技能组列表")
    @RequireLogin
    @GetMapping("/list")
    public R<List<SkillGroup>> list() {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        return R.ok(skillGroupService.listByShop(shopId));
    }

    @Operation(summary = "新增技能组")
    @RequireLogin
    @PostMapping
    public R<SkillGroup> create(@RequestBody SkillGroup group) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        group.setShopId(shopId);
        if (group.getSort() == null) group.setSort(0);
        if (group.getStatus() == null) group.setStatus(1);
        return R.ok(skillGroupService.create(group));
    }

    @Operation(summary = "更新技能组")
    @RequireLogin
    @PutMapping
    public R<SkillGroup> update(@RequestBody SkillGroup group) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        group.setShopId(shopId);
        return R.ok(skillGroupService.update(group));
    }

    @Operation(summary = "删除技能组")
    @RequireLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long shopId = UserContext.getShopId();
        if (shopId == null) return R.fail("仅商家端可用");
        skillGroupService.delete(id, shopId);
        return R.ok();
    }

    @Operation(summary = "获取员工技能组ID列表")
    @RequireLogin
    @GetMapping("/staff/{staffId}")
    public R<List<Long>> staffGroups(@PathVariable Long staffId) {
        return R.ok(skillGroupService.getStaffGroupIds(staffId));
    }

    @Operation(summary = "保存员工技能组（先删后插）")
    @RequireLogin
    @PostMapping("/staff")
    public R<Void> saveStaffGroups(@RequestBody SaveStaffGroupsRequest request) {
        skillGroupService.saveStaffGroups(request.getStaffId(), request.getGroupIds());
        return R.ok();
    }

    @Data
    public static class SaveStaffGroupsRequest {
        private Long staffId;
        private List<Long> groupIds;
    }
}