package com.byw.admin.controller;

import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/user")
@RequireAdmin
@RequiredArgsConstructor
public class AdminUserController {

    private final UserFeignClient userFeignClient;

    @GetMapping("/list")
    public R<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        // TODO: 实现用户列表查询
        Map<String, Object> result = new HashMap<>();
        result.put("list", java.util.Collections.emptyList());
        result.put("total", 0);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return R.ok(result);
    }

    @GetMapping("/{userId}")
    public R<UserDTO> getUserById(@PathVariable Long userId) {
        return userFeignClient.getUserById(userId);
    }

    @PutMapping("/{userId}/status")
    public R<Boolean> updateUserStatus(@PathVariable Long userId,
                                       @RequestParam Integer status) {
        // TODO: 实现用户状态更新（禁用/启用）
        return R.ok(true);
    }
}
