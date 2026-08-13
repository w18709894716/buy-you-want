package com.byw.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.shop.entity.MerchantAccount;
import com.byw.shop.entity.Shop;
import com.byw.shop.mapper.MerchantAccountMapper;
import com.byw.shop.mapper.ShopMapper;
import com.byw.shop.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopServiceImpl implements ShopService {

    private final ShopMapper shopMapper;
    private final MerchantAccountMapper merchantAccountMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public ShopDTO getShopById(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        return toShopDTO(shop);
    }

    @Override
    public List<ShopDTO> getShopsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return shopMapper.selectBatchIds(ids).stream().map(this::toShopDTO).toList();
    }

    @Override
    public MerchantAccountDTO getMerchantByUsername(String username) {
        MerchantAccount account = merchantAccountMapper.selectOne(
                new LambdaQueryWrapper<MerchantAccount>().eq(MerchantAccount::getUsername, username).last("limit 1"));
        return toMerchantDTO(account);
    }

    @Override
    public MerchantAccountDTO getMerchantById(Long merchantId) {
        return toMerchantDTO(merchantAccountMapper.selectById(merchantId));
    }

    @Override
    public List<MerchantAccountDTO> getMerchantsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return merchantAccountMapper.selectBatchIds(ids).stream().map(account -> {
            MerchantAccountDTO dto = toMerchantDTO(account);
            dto.setPassword(null);
            return dto;
        }).toList();
    }

    @Override
    public MerchantAccountDTO getShopOwner(Long shopId) {
        MerchantAccount account = merchantAccountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>()
                .isNull(MerchantAccount::getParentId)
                .eq(MerchantAccount::getShopId, shopId)
                .last("limit 1"));
        if (account == null) {
            return null;
        }
        MerchantAccountDTO dto = toMerchantDTO(account);
        dto.setPassword(null);
        return dto;
    }

    @Override
    public Long applyMerchant(MerchantAccount account) {
        validateApply(account);
        // 以申请账号为键的重复申请控制：审核中/已通过拒绝；被驳回凭原密码复用原记录重新提交
        MerchantAccount existing = merchantAccountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>()
                .eq(MerchantAccount::getUsername, account.getUsername())
                .orderByDesc(MerchantAccount::getId).last("limit 1"));
        Long reuseId = null;
        if (existing != null) {
            if (existing.getAuditStatus() != null && existing.getAuditStatus() == 0) {
                throw new BusinessException("该账号的入驻申请正在审核中，请勿重复提交");
            }
            if (existing.getAuditStatus() != null && existing.getAuditStatus() == 1) {
                throw new BusinessException("该账号已入驻成功，无需重复申请");
            }
            // 被驳回：需原密码匹配才允许覆盖重提，防止他人冒用账号覆盖申请记录
            if (!passwordEncoder.matches(account.getPassword(), existing.getPassword())) {
                throw new BusinessException("商家账号已存在: " + account.getUsername());
            }
            reuseId = existing.getId();
        }
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        account.setShopId(null);
        account.setRole("merchant_owner");
        account.setAuditStatus(0);
        account.setStatus(0);
        account.setRejectReason(null);
        if (reuseId != null) {
            // 驳回后重新提交：复用原记录整体覆盖
            account.setId(reuseId);
            merchantAccountMapper.updateById(account);
            log.info("商家入驻申请重新提交: id={}, username={}", reuseId, account.getUsername());
        } else {
            account.setId(null);
            merchantAccountMapper.insert(account);
            log.info("商家入驻申请提交: username={}", account.getUsername());
        }
        return account.getId();
    }

    /**
     * 入驻申请参数校验（按入驻类型区分必传材料）
     */
    private void validateApply(MerchantAccount account) {
        if (isBlank(account.getUsername()) || isBlank(account.getPassword())) {
            throw new BusinessException("登录账号和密码不能为空");
        }
        if (isBlank(account.getShopName())) {
            throw new BusinessException("店铺名称不能为空");
        }
        if (isBlank(account.getRealName()) || isBlank(account.getPhone())) {
            throw new BusinessException("联系人姓名和联系电话不能为空");
        }
        if (account.getAgreementSigned() == null || account.getAgreementSigned() != 1) {
            throw new BusinessException("请先阅读并同意入驻协议");
        }
        Integer type = account.getMerchantType();
        if (type == null || (type != 1 && type != 2)) {
            throw new BusinessException("请选择入驻类型");
        }
        if (type == 1 && (isBlank(account.getIdCardFront()) || isBlank(account.getIdCardBack()))) {
            throw new BusinessException("个人入驻需上传身份证人像面和国徽面照片");
        }
        if (type == 2) {
            if (isBlank(account.getCompanyName())) {
                throw new BusinessException("企业入驻需填写企业名称");
            }
            if (isBlank(account.getBusinessLicense())) {
                throw new BusinessException("企业入驻需上传营业执照照片");
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public MerchantAccountDTO getApplyByAccount(String username, String rawPassword) {
        if (isBlank(username) || isBlank(rawPassword)) {
            return null;
        }
        MerchantAccount account = merchantAccountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>()
                .eq(MerchantAccount::getUsername, username)
                .orderByDesc(MerchantAccount::getId).last("limit 1"));
        // 无记录或密码不匹配统一返回 null，避免账号枚举
        if (account == null || !passwordEncoder.matches(rawPassword, account.getPassword())) {
            return null;
        }
        MerchantAccountDTO dto = toMerchantDTO(account);
        dto.setPassword(null);
        return dto;
    }

    @Override
    public PageResult<MerchantAccountDTO> listMerchants(Integer pageNum, Integer pageSize, Integer auditStatus) {
        Page<MerchantAccount> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MerchantAccount> wrapper = new LambdaQueryWrapper<MerchantAccount>()
                .eq(auditStatus != null, MerchantAccount::getAuditStatus, auditStatus)
                .orderByDesc(MerchantAccount::getId);
        Page<MerchantAccount> result = merchantAccountMapper.selectPage(page, wrapper);
        List<MerchantAccountDTO> list = result.getRecords().stream().map(this::toMerchantDTO).toList();
        return PageResult.of(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveMerchant(Long merchantId, String shopName) {
        MerchantAccount account = merchantAccountMapper.selectById(merchantId);
        if (account == null) {
            throw new BusinessException("商家账号不存在");
        }
        if (account.getAuditStatus() != null && account.getAuditStatus() == 1) {
            throw new BusinessException("该商家已审核通过");
        }
        Shop shop = new Shop();
        // 店名优先级：管理员传参 > 申请时意向店名 > 兜底
        String name = shopName != null && !shopName.isBlank() ? shopName
                : (account.getShopName() != null && !account.getShopName().isBlank() ? account.getShopName()
                : account.getRealName() + "的店铺");
        shop.setName(name);
        shop.setMerchantId(merchantId);
        shop.setContactName(account.getRealName());
        shop.setContactPhone(account.getPhone());
        shop.setSelfOperated(1);
        shop.setStatus(1);
        shopMapper.insert(shop);

        account.setShopId(shop.getId());
        account.setAuditStatus(1);
        account.setStatus(1);
        account.setRejectReason(null);
        merchantAccountMapper.updateById(account);
        log.info("商家审核通过: merchantId={}, shopId={}", merchantId, shop.getId());
    }

    @Override
    public void rejectMerchant(Long merchantId, String rejectReason) {
        MerchantAccount account = merchantAccountMapper.selectById(merchantId);
        if (account == null) {
            throw new BusinessException("商家账号不存在");
        }
        account.setAuditStatus(2);
        account.setStatus(0);
        account.setRejectReason(rejectReason);
        merchantAccountMapper.updateById(account);
        log.info("商家审核驳回: merchantId={}, reason={}", merchantId, rejectReason);
    }

    @Override
    public PageResult<ShopDTO> listShops(Integer pageNum, Integer pageSize, Integer status) {
        Page<Shop> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(status != null, Shop::getStatus, status)
                .orderByDesc(Shop::getId);
        Page<Shop> result = shopMapper.selectPage(page, wrapper);
        List<ShopDTO> list = result.getRecords().stream().map(this::toShopDTO).toList();
        return PageResult.of(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public void updateShopStatus(Long shopId, Integer status) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        shop.setStatus(status);
        shopMapper.updateById(shop);
    }

    @Override
    public void updateShop(ShopDTO shopDTO) {
        if (shopDTO.getId() == null) {
            throw new BusinessException("店铺ID不能为空");
        }
        Shop shop = shopMapper.selectById(shopDTO.getId());
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        // 仅允许商家修改展示信息，状态/归属由平台控制
        shop.setName(shopDTO.getName());
        shop.setLogo(shopDTO.getLogo());
        shop.setDescription(shopDTO.getDescription());
        shop.setContactName(shopDTO.getContactName());
        shop.setContactPhone(shopDTO.getContactPhone());
        shopMapper.updateById(shop);
    }

    // ========== 商家子账号（员工）管理 ==========

    @Override
    public PageResult<MerchantAccountDTO> listStaff(Long parentId, Integer pageNum, Integer pageSize) {
        Page<MerchantAccount> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MerchantAccount> wrapper = new LambdaQueryWrapper<MerchantAccount>()
                .eq(MerchantAccount::getParentId, parentId)
                .orderByDesc(MerchantAccount::getId);
        Page<MerchantAccount> result = merchantAccountMapper.selectPage(page, wrapper);
        List<MerchantAccountDTO> list = result.getRecords().stream().map(account -> {
            MerchantAccountDTO dto = toMerchantDTO(account);
            dto.setPassword(null);
            return dto;
        }).toList();
        return PageResult.of(list, result.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<MerchantAccountDTO> listActiveStaffByShop(Long shopId, Integer limit) {
        int max = limit == null || limit < 1 ? 200 : Math.min(limit, 500);
        List<MerchantAccount> accounts = merchantAccountMapper.selectList(
                new LambdaQueryWrapper<MerchantAccount>()
                        .eq(MerchantAccount::getShopId, shopId)
                        .isNotNull(MerchantAccount::getParentId)
                        .eq(MerchantAccount::getStatus, 1)
                        .orderByDesc(MerchantAccount::getId)
                        .last("limit " + max));
        return accounts.stream().map(account -> {
            MerchantAccountDTO dto = toMerchantDTO(account);
            dto.setPassword(null);
            return dto;
        }).toList();
    }

    @Override
    public Long createStaff(Long parentId, Long shopId, MerchantAccountDTO dto) {
        if (isBlank(dto.getUsername()) || isBlank(dto.getPassword())) {
            throw new BusinessException("用户名和密码不能为空");
        }
        MerchantAccount existing = merchantAccountMapper.selectOne(new LambdaQueryWrapper<MerchantAccount>()
                .eq(MerchantAccount::getUsername, dto.getUsername()).last("limit 1"));
        if (existing != null) {
            throw new BusinessException("账号已存在: " + dto.getUsername());
        }
        MerchantAccount account = new MerchantAccount();
        account.setUsername(dto.getUsername());
        account.setPassword(passwordEncoder.encode(dto.getPassword()));
        account.setRealName(dto.getRealName());
        account.setPhone(dto.getPhone());
        // 子账号继承主账号店铺，直接启用，无需入驻审核
        account.setParentId(parentId);
        account.setShopId(shopId);
        account.setRole("merchant_staff");
        account.setAuditStatus(1);
        account.setStatus(1);
        merchantAccountMapper.insert(account);
        log.info("新建商家子账号: parentId={}, shopId={}, username={}", parentId, shopId, dto.getUsername());
        return account.getId();
    }

    @Override
    public void updateStaffStatus(Long parentId, Long staffId, Integer status) {
        MerchantAccount account = requireOwnedStaff(parentId, staffId);
        account.setStatus(status);
        merchantAccountMapper.updateById(account);
    }

    @Override
    public void resetStaffPassword(Long parentId, Long staffId, String password) {
        if (isBlank(password)) {
            throw new BusinessException("密码不能为空");
        }
        MerchantAccount account = requireOwnedStaff(parentId, staffId);
        account.setPassword(passwordEncoder.encode(password));
        merchantAccountMapper.updateById(account);
    }

    /** 校验子账号存在且归属当前主账号，防止越权操作他店员工 */
    private MerchantAccount requireOwnedStaff(Long parentId, Long staffId) {
        MerchantAccount account = merchantAccountMapper.selectById(staffId);
        if (account == null || !parentId.equals(account.getParentId())) {
            throw new BusinessException("子账号不存在或无权操作");
        }
        return account;
    }

    private ShopDTO toShopDTO(Shop shop) {
        if (shop == null) {
            return null;
        }
        ShopDTO dto = new ShopDTO();
        BeanUtils.copyProperties(shop, dto);
        return dto;
    }

    private MerchantAccountDTO toMerchantDTO(MerchantAccount account) {
        if (account == null) {
            return null;
        }
        MerchantAccountDTO dto = new MerchantAccountDTO();
        BeanUtils.copyProperties(account, dto);
        return dto;
    }
}
