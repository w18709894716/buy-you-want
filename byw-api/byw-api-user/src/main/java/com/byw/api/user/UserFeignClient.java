package com.byw.api.user;

import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "byw-user", contextId = "userFeignClient")
public interface UserFeignClient {

    @GetMapping("/feign/user/{userId}")
    R<UserDTO> getUserById(@PathVariable("userId") Long userId);

    @GetMapping("/feign/user/username/{username}")
    R<UserDTO> getUserByUsername(@PathVariable("username") String username);

    @GetMapping("/feign/user/phone/{phone}")
    R<UserDTO> getUserByPhone(@PathVariable("phone") String phone);

    @PostMapping("/feign/user")
    R<Long> createUser(@RequestBody UserDTO userDTO);

    @PostMapping("/feign/user/check-username/{username}")
    R<Boolean> checkUsernameExists(@PathVariable("username") String username);

    @PostMapping("/feign/user/validate-password")
    R<Boolean> validatePassword(@RequestParam("username") String username, @RequestParam("password") String password);

    @GetMapping("/feign/user/list")
    R<PageResult<UserDTO>> listUsers(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                     @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                     @RequestParam(value = "keyword", required = false) String keyword);

    @PutMapping("/feign/user/{userId}/status")
    R<Boolean> updateUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") Integer status);

    @GetMapping("/feign/user/count")
    R<Long> countUsers();

    @GetMapping("/feign/user/count-today")
    R<Long> countTodayNewUsers();
}
