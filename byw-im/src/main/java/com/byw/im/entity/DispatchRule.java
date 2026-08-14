package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 客服分流规则：商家配置匹配条件（入口意图/订单状态）与服务时间，
 * 命中后消息落到绑定的分组，组内按客服权重均衡分配。
 */
@Data
@TableName("t_im_dispatch_rule")
public class DispatchRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 店铺ID */
    private Long shopId;

    /** 规则名称 */
    private String ruleName;

    /** 优先智能机器人 0否 1是（服务时间内有效） */
    private Integer robotFirst;

    /** 服务开始 HH:mm（空=全天） */
    private String serviceStart;

    /** 服务结束 HH:mm（小于开始表示跨天） */
    private String serviceEnd;

    /** 非服务时间提示语 */
    private String offHoursTip;

    /** 回头客 0否 1是（窗口内最近接待过该用户的客服优先） */
    private Integer repeatCustomer;

    /** 回头客时间窗口(小时)：24/48/72/自定义整数；空=24 */
    private Integer repeatWindowHours;

    /** 匹配条件-入口意图（逗号分隔：product-商品详情页 order-订单页 shop-店铺首页 default-普通咨询；NULL=不按意图） */
    private String intents;

    /** 匹配条件-订单状态（逗号分隔状态码；NULL=不按订单状态） */
    private String orderStatuses;

    /** 匹配分组ID（命中后进入该分组按客服权重分配） */
    private Long groupId;

    /** 优先级（数字越小越优先匹配） */
    private Integer priority;

    /** 是否启用 0禁用 1启用 */
    private Integer enabled;

    /** 匹配分组名称（非持久化，规则列表展示用快照） */
    @TableField(exist = false)
    private String groupName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
