package com.byw.admin.controller;

import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/user")
@RequireAdmin
@RequiredArgsConstructor
public class AdminUserController {

    private final UserFeignClient userFeignClient;

    @GetMapping("/list")
    public R<PageResult<UserDTO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword) {
        return userFeignClient.listUsers(pageNum, pageSize, keyword);
    }

    @GetMapping("/{userId}")
    public R<UserDTO> getUserById(@PathVariable Long userId) {
        return userFeignClient.getUserById(userId);
    }

    @PutMapping("/{userId}/status")
    public R<Boolean> updateUserStatus(@PathVariable Long userId,
                                       @RequestParam Integer status) {
        return userFeignClient.updateUserStatus(userId, status);
    }
}
