package com.byw.user.feign;

import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.R;
import com.byw.user.entity.User;
import com.byw.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feign/user")
@RequiredArgsConstructor
public class UserFeignImpl implements UserFeignClient {

    private final UserService userService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/{userId}")
    public R<UserDTO> getUserById(@PathVariable Long userId) {
        User user = userService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return R.ok(dto);
    }

    @GetMapping("/username/{username}")
    public R<UserDTO> getUserByUsername(@PathVariable String username) {
        User user = userService.getByUsername(username);
        if (user == null) return R.fail("用户不存在");
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return R.ok(dto);
    }

    @GetMapping("/phone/{phone}")
    public R<UserDTO> getUserByPhone(@PathVariable String phone) {
        User user = userService.getByPhone(phone);
        if (user == null) return R.fail("用户不存在");
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        return R.ok(dto);
    }

    @PostMapping
    public R<Long> createUser(@RequestBody UserDTO userDTO) {
        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        // Use provided password if available, otherwise default
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        } else if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode("123456"));
        }
        userService.save(user);
        return R.ok(user.getId());
    }

    @PostMapping("/check-username/{username}")
    public R<Boolean> checkUsernameExists(@PathVariable String username) {
        return R.ok(userService.checkUsernameExists(username));
    }

    @PostMapping("/validate-password")
    public R<Boolean> validatePassword(@RequestParam("username") String username, @RequestParam("password") String password) {
        User user = userService.getByUsername(username);
        if (user == null) {
            return R.fail("用户不存在");
        }
        boolean matches = passwordEncoder.matches(password, user.getPassword());
        return R.ok(matches);
    }
}
