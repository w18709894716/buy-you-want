package com.byw.common.core.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),

    // Auth
    UNAUTHORIZED(401, "未登录或Token已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),

    // Validation
    PARAM_ERROR(400, "参数错误"),
    PARAM_MISSING(400, "缺少必要参数"),

    // Business
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),
    USERNAME_EXISTS(1003, "用户名已存在"),
    PHONE_EXISTS(1004, "手机号已存在"),
    PASSWORD_ERROR(1005, "密码错误"),
    SMS_CODE_ERROR(1006, "验证码错误"),
    SMS_CODE_EXPIRED(1007, "验证码已过期"),

    // Product
    PRODUCT_NOT_FOUND(2001, "商品不存在"),
    PRODUCT_OFF_SHELF(2002, "商品已下架"),
    SKU_NOT_FOUND(2003, "SKU不存在"),
    STOCK_NOT_ENOUGH(2004, "库存不足"),

    // Cart
    CART_ITEM_NOT_FOUND(3001, "购物车商品不存在"),

    // Order
    ORDER_NOT_FOUND(4001, "订单不存在"),
    ORDER_STATUS_ERROR(4002, "订单状态异常"),
    ORDER_ALREADY_PAID(4003, "订单已支付"),
    ORDER_TIMEOUT(4004, "订单已超时"),

    // Pay
    PAY_FAILED(5001, "支付失败"),
    PAY_CHANNEL_ERROR(5002, "支付渠道异常"),
    REFUND_FAILED(5003, "退款失败"),

    // Promotion
    COUPON_NOT_FOUND(6001, "优惠券不存在"),
    COUPON_EXPIRED(6002, "优惠券已过期"),
    COUPON_ALREADY_CLAIMED(6003, "已领取过该优惠券"),
    COUPON_NOT_ENOUGH(6004, "优惠券已领完"),
    SECKILL_NOT_START(6005, "秒杀未开始"),
    SECKILL_ENDED(6006, "秒杀已结束"),
    SECKILL_SOLD_OUT(6007, "秒杀已售罄"),
    SECKILL_REPEAT(6008, "不能重复秒杀"),

    // Review
    REVIEW_ALREADY_EXISTS(7001, "已评价过该订单"),
    ORDER_NOT_COMPLETED(7002, "订单未完成，无法评价"),

    // System
    SYSTEM_ERROR(9999, "系统内部错误"),
    RATE_LIMIT(9001, "请求过于频繁，请稍后再试"),
    IDEMPOTENT_ERROR(9002, "请勿重复提交");

    private final int code;
    private final String message;
}
