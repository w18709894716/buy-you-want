package com.byw.user.controller;

import com.byw.api.shop.ShopFeignClient;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.Public;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.user.service.UserShopFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "店铺关注", description = "用户店铺关注管理")
@RestController
@RequestMapping("/user/shop-follow")
@RequiredArgsConstructor
@Slf4j
public class UserShopFollowController {

    private final UserShopFollowService userShopFollowService;
    private final ShopFeignClient shopFeignClient;

    @Operation(summary = "关注店铺")
    @PostMapping("/{shopId}")
    @RequireLogin
    public R<Void> follow(@PathVariable Long shopId) {
        userShopFollowService.follow(UserContext.getUserId(), shopId);
        return R.ok();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{shopId}")
    @RequireLogin
    public R<Void> unfollow(@PathVariable Long shopId) {
        userShopFollowService.unfollow(UserContext.getUserId(), shopId);
        return R.ok();
    }

    @Operation(summary = "关注状态与粉丝数（匿名可查）")
    @GetMapping("/status/{shopId}")
    @Public
    public R<FollowStatusVO> status(@PathVariable Long shopId) {
        FollowStatusVO vo = new FollowStatusVO();
        Long userId = UserContext.getUserId();
        vo.setFollowed(userId != null && userShopFollowService.isFollowing(userId, shopId));
        vo.setFollowerCount(userShopFollowService.followerCount(shopId));
        return R.ok(vo);
    }

    @Operation(summary = "已关注的店铺列表")
    @GetMapping("/list")
    @RequireLogin
    public R<List<FollowedShopVO>> list() {
        List<Long> shopIds = userShopFollowService.followedShopIds(UserContext.getUserId());
        if (shopIds.isEmpty()) {
            return R.ok(List.of());
        }
        // 批量回填店铺名称/Logo，失败降级为仅返回 shopId
        Map<Long, ShopDTO> shopMap = Map.of();
        try {
            R<List<ShopDTO>> result = shopFeignClient.getShopsByIds(shopIds);
            if (result.isSuccess() && result.getData() != null) {
                shopMap = result.getData().stream()
                        .collect(Collectors.toMap(ShopDTO::getId, Function.identity(), (a, b) -> a));
            }
        } catch (Exception e) {
            log.warn("回填关注店铺信息失败: {}", e.getMessage());
        }
        List<FollowedShopVO> list = new ArrayList<>();
        for (Long shopId : shopIds) {
            FollowedShopVO vo = new FollowedShopVO();
            vo.setShopId(shopId);
            ShopDTO shop = shopMap.get(shopId);
            if (shop != null) {
                vo.setShopName(shop.getName());
                vo.setLogo(shop.getLogo());
            }
            list.add(vo);
        }
        return R.ok(list);
    }

    /** 关注状态 */
    @Data
    public static class FollowStatusVO {
        private Boolean followed;
        private Long followerCount;
    }

    /** 已关注店铺 */
    @Data
    public static class FollowedShopVO {
        private Long shopId;
        private String shopName;
        private String logo;
    }
}
