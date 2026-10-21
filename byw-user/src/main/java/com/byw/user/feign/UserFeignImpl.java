package com.byw.user.feign;

import jakarta.validation.Valid;
import com.byw.common.log.annotation.OperationLog;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.AddressDTO;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.constant.CacheNames;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.InternalApi;
import com.byw.user.entity.User;
import com.byw.user.entity.UserAddress;
import com.byw.user.mapper.UserAddressMapper;
import com.byw.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/feign/user")
@RequiredArgsConstructor
@InternalApi
public class UserFeignImpl implements UserFeignClient {

    private final UserService userService;
    private final UserAddressMapper userAddressMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/{userId}")
    public R<UserDTO> getUserById(@PathVariable Long userId) {
        User user = userService.getUserDetail(userId);
        if (user == null) return R.fail("用户不存在");
        return R.ok(toSafeDTO(user));
    }

    @Override
    @GetMapping("/batch")
    public R<List<UserDTO>> getUsersByIds(@RequestParam("ids") List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return R.ok(java.util.Collections.emptyList());
        }
        List<UserDTO> list = userService.listByIds(ids).stream()
                .map(this::toSafeDTO)
                .collect(Collectors.toList());
        return R.ok(list);
    }

    /**
     * 按用户名查询（**登录校验专用**）。
     *
     * <p>这是唯一会返回 password 哈希的服务间接口：byw-auth 需要它做 BCrypt 比对。
     * 其他任何接口都不得外传哈希，否则管理端列表、IM、评价等链路都会把凭据泄露给前端。
     */
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
        return R.ok(toSafeDTO(user));
    }

    @PostMapping
    public R<Long> createUser(@Valid @RequestBody UserDTO userDTO) {
        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        // Use provided password if available, otherwise default
        if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        } else if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode("123456"));
        }
        try {
            userService.save(user);
        } catch (DuplicateKeyException e) {
            // 并发注册穿透前置校验时，根据冲突的唯一键返回友好提示
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("phone")) {
                return R.fail("手机号已被注册");
            }
            if (msg.contains("username")) {
                return R.fail("用户名已存在");
            }
            return R.fail("账号信息重复，请检查用户名或手机号");
        }
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

    /**
     * 出网前抹掉密码哈希。
     *
     * <p>BCrypt 串属于凭据，一旦随管理端列表 / IM / 评价等接口返回，前端、日志、抓包都会留下副本，
     * 且可被离线暴力破解。除 {@link #getUserByUsername(String)}（登录校验）外一律置空。
     */
    private UserDTO toSafeDTO(User user) {
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user, dto);
        dto.setPassword(null);
        return dto;
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
        List<UserDTO> dtoList = page.getRecords().stream()
                .map(this::toSafeDTO)
                .collect(Collectors.toList());
        return R.ok(PageResult.of(dtoList, page.getTotal(), pageNum, pageSize));
    }

    @Override
    @OperationLog(module = "用户", operation = "状态变更", description = "启用/禁用会员账号")
    @PutMapping("/{userId}/status")
    @CacheInvalidate(name = CacheNames.USER_INFO, key = "#userId")
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
