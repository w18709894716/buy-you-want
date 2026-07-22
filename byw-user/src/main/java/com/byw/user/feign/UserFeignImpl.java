package com.byw.user.feign;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.AddressDTO;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.user.entity.User;
import com.byw.user.entity.UserAddress;
import com.byw.user.mapper.UserAddressMapper;
import com.byw.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/user")
@RequiredArgsConstructor
public class UserFeignImpl implements UserFeignClient {

    private final UserService userService;
    private final UserAddressMapper userAddressMapper;
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

    @Override
    @GetMapping("/list")
    public R<PageResult<UserDTO>> listUsers(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                                            @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword);
        }
        wrapper.orderByDesc(User::getCreatedAt);
        IPage<User> page = userService.page(new Page<>(pageNum, pageSize), wrapper);
        List<UserDTO> dtoList = page.getRecords().stream().map(user -> {
            UserDTO dto = new UserDTO();
            BeanUtils.copyProperties(user, dto);
            return dto;
        }).collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @PutMapping("/{userId}/status")
    public R<Boolean> updateUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") Integer status) {
        User user = userService.getById(userId);
        if (user == null) return R.fail("用户不存在");
        user.setStatus(status);
        return R.ok(userService.updateById(user));
    }

    @Override
    @GetMapping("/count")
    public R<Long> countUsers() {
        return R.ok(userService.count());
    }

    @Override
    @GetMapping("/count-today")
    public R<Long> countTodayNewUsers() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long count = userService.count(new LambdaQueryWrapper<User>()
                .ge(User::getCreatedAt, todayStart));
        return R.ok(count);
    }

    @Override
    @GetMapping("/address/{addressId}")
    public R<AddressDTO> getAddressById(@PathVariable("addressId") Long addressId) {
        UserAddress address = userAddressMapper.selectById(addressId);
        if (address == null) return R.fail("地址不存在");
        AddressDTO dto = new AddressDTO();
        BeanUtils.copyProperties(address, dto);
        return R.ok(dto);
    }

    @Override
    @GetMapping("/{userId}/default-address")
    public R<AddressDTO> getDefaultAddress(@PathVariable("userId") Long userId) {
        UserAddress address = userAddressMapper.selectOne(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getIsDefault, 1)
                        .last("LIMIT 1"));
        // 如果没有默认地址，取第一个
        if (address == null) {
            address = userAddressMapper.selectOne(
                    new LambdaQueryWrapper<UserAddress>()
                            .eq(UserAddress::getUserId, userId)
                            .orderByDesc(UserAddress::getCreatedAt)
                            .last("LIMIT 1"));
        }
        if (address == null) return R.fail("无收货地址");
        AddressDTO dto = new AddressDTO();
        BeanUtils.copyProperties(address, dto);
        return R.ok(dto);
    }
}
