package com.byw.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.exception.ResultCode;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.user.entity.UserAddress;
import com.byw.user.mapper.UserAddressMapper;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收货地址", description = "收货地址管理")
@RestController
@RequestMapping("/user/address")
@RequireLogin
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressMapper userAddressMapper;

    @Operation(summary = "获取地址列表")
    @GetMapping("/list")
    public R<List<UserAddress>> list() {
        Long userId = UserContext.getUserId();
        return R.ok(userAddressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public R<Void> add(@Valid @RequestBody UserAddress address) {
        Long userId = UserContext.getUserId();
        address.setUserId(userId);
        // 若新增地址设为默认，先清除该用户其他默认地址，避免出现多个默认
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        userAddressMapper.insert(address);
        return R.ok();
    }

    @Operation(summary = "更新地址")
    @PutMapping
    public R<Void> update(@Valid @RequestBody UserAddress address) {
        Long userId = UserContext.getUserId();
        // 归属校验：入参 id 由前端传，先 setUserId(当前用户) 再 updateById 会把他人地址整条改挂到自己名下
        requireOwned(address.getId());
        address.setUserId(userId);
        // 若更新为默认地址，先清除该用户其他默认地址，避免出现多个默认
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        userAddressMapper.updateById(address);
        return R.ok();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        UserAddress address = userAddressMapper.selectById(id);
        if (address != null) {
            // 删除前校验地址归属当前登录用户，防越权删除他人地址
            if (!UserContext.getUserId().equals(address.getUserId())) {
                throw new BusinessException(ResultCode.FORBIDDEN);
            }
            userAddressMapper.deleteById(id);
        }
        return R.ok();
    }

    @Operation(summary = "设为默认地址")
    @PutMapping("/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        // 归属校验：否则可以把他人地址的 userId 覆写成自己，等于把地址据为己有
        requireOwned(id);
        // Clear existing default
        clearDefault(userId);
        // Set new default
        UserAddress setDefault = new UserAddress();
        setDefault.setId(id);
        setDefault.setIsDefault(1);
        setDefault.setUserId(userId);
        userAddressMapper.updateById(setDefault);
        return R.ok();
    }

    /**
     * 校验地址归属当前登录用户。
     *
     * <p>地址不存在也按 FORBIDDEN 处理，与"存在但不属于你"表现一致，避免用 id 探测他人地址是否存在。
     */
    private void requireOwned(Long id) {
        UserAddress address = id == null ? null : userAddressMapper.selectById(id);
        if (address == null || !UserContext.getUserId().equals(address.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 清除指定用户的所有默认地址标记 */
    private void clearDefault(Long userId) {
        UserAddress clearDefault = new UserAddress();
        clearDefault.setIsDefault(0);
        userAddressMapper.update(clearDefault, new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .eq(UserAddress::getIsDefault, 1));
    }
}
