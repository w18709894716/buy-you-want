package com.byw.order.controller;

import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.common.security.annotation.RequireLogin;
import com.byw.common.security.context.UserContext;
import com.byw.order.entity.AfterSale;
import com.byw.order.service.AfterSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "售后", description = "订单售后管理")
@Slf4j
@RestController
@RequestMapping("/order/aftersale")
@RequiredArgsConstructor
@RequireLogin
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @Operation(summary = "提交售后申请")
    @PostMapping("/apply")
    public R<String> apply(@Valid @RequestBody AfterSaleApplyDTO dto) {
        String afterSaleNo = afterSaleService.apply(dto.getOrderNo(), UserContext.getUserId(), dto.getOrderItemId(),
                dto.getType(), dto.getReason(), dto.getDescription(), dto.getRefundAmount());
        return R.ok(afterSaleNo);
    }

    @Operation(summary = "我的售后单列表")
    @GetMapping("/my")
    public R<PageResult<AfterSale>> my(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return R.ok(afterSaleService.getUserAfterSales(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "查询订单进行中的售后单")
    @GetMapping("/active/{orderNo}")
    public R<AfterSale> active(@PathVariable String orderNo) {
        AfterSale afterSale = afterSaleService.getActiveByOrderNo(orderNo);
        // 仅返回归属当前用户的售后单，避免越权探测
        if (afterSale != null && !afterSale.getUserId().equals(UserContext.getUserId())) {
            return R.ok(null);
        }
        return R.ok(afterSale);
    }

    @Operation(summary = "撤销售后申请")
    @PostMapping("/cancel/{id}")
    public R<Void> cancel(@PathVariable Long id) {
        afterSaleService.cancel(id, UserContext.getUserId());
        return R.ok();
    }

    @Operation(summary = "买家填写寄回运单号")
    @PostMapping("/return-shipping/{id}")
    public R<Void> returnShipping(@PathVariable Long id, @Valid @RequestBody ReturnShippingDTO dto) {
        afterSaleService.fillReturnShipping(id, UserContext.getUserId(), dto.getCompany(), dto.getTrackingNo());
        return R.ok();
    }

    @Operation(summary = "查看退款明细")
    @GetMapping("/refund-detail/{orderNo}")
    public R<com.byw.order.dto.RefundDetailDTO> refundDetail(@PathVariable String orderNo,
                                                             @RequestParam(required = false) Long itemId) {
        return R.ok(afterSaleService.getRefundDetail(orderNo, UserContext.getUserId(), itemId));
    }

    /** 售后申请入参 */
    @Data
    public static class AfterSaleApplyDTO {
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
        /** 订单明细ID（商品级售后，必填） */
        @NotNull(message = "请选择要申请售后的商品")
        private Long orderItemId;
        /** 售后类型 1仅退款 2退货退款 3换货 4维修 5补寄 6价保 */
        @NotNull(message = "请选择售后类型")
        @Min(value = 1, message = "售后类型不合法")
        @Max(value = 6, message = "售后类型不合法")
        private Integer type;
        @Size(max = 200, message = "申请原因过长")
        private String reason;
        @Size(max = 500, message = "问题描述过长")
        private String description;
        /** 退款金额：仅退款类必填，由业务层按明细小计校验，这里不做必填约束 */
        private BigDecimal refundAmount;
    }

    /** 买家寄回信息入参 */
    @Data
    public static class ReturnShippingDTO {
        /** 寄回物流公司：对应 VARCHAR(50) NOT NULL */
        @NotBlank(message = "请选择寄回物流公司")
        @Size(max = 50, message = "物流公司名称过长")
        private String company;
        /** 寄回运单号：对应 VARCHAR(64) NOT NULL */
        @NotBlank(message = "请填写寄回运单号")
        @Size(max = 64, message = "运单号过长")
        private String trackingNo;
    }
}
