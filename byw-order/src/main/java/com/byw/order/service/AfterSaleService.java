package com.byw.order.service;

import com.byw.common.core.result.PageResult;
import com.byw.order.dto.RefundDetailDTO;
import com.byw.order.entity.AfterSale;

import java.math.BigDecimal;

public interface AfterSaleService {

    /**
     * 提交商品级售后申请（订单交易完成/退款中可申请，按订单明细维度）
     *
     * @param orderNo      订单号
     * @param userId       申请用户ID
     * @param orderItemId  订单明细ID（必填，需归属该订单）
     * @param type         售后类型 1仅退款 2退货退款 3换货 4维修 5补寄 6价保
     * @param reason       申请原因
     * @param description  问题描述
     * @param refundAmount 申请退款金额（退款类售后必填，上限为明细小计）
     * @return 售后单号
     */
    String apply(String orderNo, Long userId, Long orderItemId, Integer type, String reason, String description, BigDecimal refundAmount);

    /** 分页查询我的售后单 */
    PageResult<AfterSale> getUserAfterSales(Long userId, Integer pageNum, Integer pageSize);

    /** 查询某订单进行中（待审核/处理中）的售后单，无则返回null */
    AfterSale getActiveByOrderNo(String orderNo);

    /** 撤销售后申请（仅待审核可撤销，需校验归属） */
    void cancel(Long id, Long userId);

    /** 分页查询本店售后单（按当前商家 shopId 过滤，供商家审核） */
    PageResult<AfterSale> getShopAfterSales(Integer status, Integer pageNum, Integer pageSize);

    /** 商家审核通过：仅退款直接退款；退货退款转待买家寄回；其他类型置完成（需校验本店归属与待审核状态） */
    void approve(Long id);

    /** 商家审核拒绝（需校验本店归属与待审核状态） */
    void reject(Long id, String reason);

    /** 买家填写寄回物流单号（需校验归属与待买家寄回状态），推进为待商家收货 */
    void fillReturnShipping(Long id, Long userId, String company, String trackingNo);

    /** 商家确认收货（需校验本店归属与待商家收货状态），触发退款 */
    void confirmReturnReceived(Long id);

    /** 组装退款明细（退款流程时间线 + 到账信息，需校验归属）；itemId 非空时定位商品级售后，为空取最新一条（兼容历史） */
    RefundDetailDTO getRefundDetail(String orderNo, Long userId, Long itemId);
}
