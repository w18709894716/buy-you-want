package com.byw.shop.service;

import com.byw.api.shop.dto.MerchantAccountDTO;
import com.byw.api.shop.dto.ShopDTO;
import com.byw.common.core.result.PageResult;
import com.byw.shop.entity.MerchantAccount;

import java.util.List;

public interface ShopService {

    /**
     * 根据ID查询店铺
     */
    ShopDTO getShopById(Long shopId);

    /**
     * 批量查询店铺（用于订单/商品聚合展示店铺名）
     */
    List<ShopDTO> getShopsByIds(List<Long> ids);

    /**
     * 根据登录名查询商家账号（供 byw-auth 商家登录使用，含密码）
     */
    MerchantAccountDTO getMerchantByUsername(String username);

    /**
     * 根据ID查询商家账号
     */
    MerchantAccountDTO getMerchantById(Long merchantId);

    /**
     * 批量查询商家账号（角色成员列表聚合用，脱敏不含密码）
     */
    List<MerchantAccountDTO> getMerchantsByIds(List<Long> ids);

    /**
     * 根据店铺ID查询主账号（parent_id IS NULL 且 shop_id 匹配；IM 客服兜底候选用，脱敏不含密码）
     */
    MerchantAccountDTO getShopOwner(Long shopId);

    /**
     * 商家入驻申请（创建待审核商家账号；以申请账号为键防重复，驳回后凭原密码重新提交复用原记录）
     */
    Long applyMerchant(MerchantAccount account);

    /**
     * 凭申请账号+密码查询入驻申请进度（无记录或密码不匹配统一返回 null 防枚举，脱敏不含密码）
     */
    MerchantAccountDTO getApplyByAccount(String username, String rawPassword);

    /**
     * 平台端：分页查询商家入驻申请
     */
    PageResult<MerchantAccountDTO> listMerchants(Integer pageNum, Integer pageSize, Integer auditStatus);

    /**
     * 平台端：审核通过（创建店铺并回填 shopId，账号启用）
     */
    void approveMerchant(Long merchantId, String shopName);

    /**
     * 平台端：审核驳回
     */
    void rejectMerchant(Long merchantId, String rejectReason);

    /**
     * 平台端：分页查询店铺
     */
    PageResult<ShopDTO> listShops(Integer pageNum, Integer pageSize, Integer status);

    /**
     * 平台端：更新店铺状态（0关店 1营业 2封禁）
     */
    void updateShopStatus(Long shopId, Integer status);

    /**
     * 商家端：更新自己的店铺信息
     */
    void updateShop(ShopDTO shopDTO);

    // ========== 商家子账号（员工）管理 ==========

    /**
     * 分页查询本店子账号（parentId=当前主账号ID）
     */
    PageResult<MerchantAccountDTO> listStaff(Long parentId, Integer pageNum, Integer pageSize);

    /**
     * 按店铺查询启用中的子账号（不依赖调用者身份，供下拉选项等场景）
     */
    List<MerchantAccountDTO> listActiveStaffByShop(Long shopId, Integer limit);

    /**
     * 新建子账号（parent_id=主账号、shop_id 继承、audit_status=1、role=merchant_staff、入驻资料留空）
     */
    Long createStaff(Long parentId, Long shopId, MerchantAccountDTO dto);

    /**
     * 启停子账号（校验归属当前主账号）
     */
    void updateStaffStatus(Long parentId, Long staffId, Integer status);

    /**
     * 重置子账号密码（校验归属当前主账号）
     */
    void resetStaffPassword(Long parentId, Long staffId, String password);
}
