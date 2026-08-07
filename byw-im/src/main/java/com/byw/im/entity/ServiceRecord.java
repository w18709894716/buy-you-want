package com.byw.im.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * IM 服务记录：一次服务 = 一次评价单元。
 * 服务开始：客服接入 / 上次服务结束后再次发消息；
 * 服务结束：双方超时（默认 10 分钟）自动结束；
 * 客服掉线不算结束，会话重新分配后由新客服继续服务。
 * 评价对象始终为最终处理人（staffId，转接/接管时更新，介入不更新）。
 */
@Data
@TableName("t_im_service_record")
public class ServiceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联会话ID */
    private Long conversationId;

    /** 店铺ID */
    private Long shopId;

    /** 用户ID */
    private Long userId;

    /** 服务状态：IN_PROGRESS-进行中 ENDED-已结束 RATED-已评价 */
    private String status;

    /** 最终处理客服ID（评价对象） */
    private Long staffId;

    /** 客服姓名快照 */
    private String staffName;

    /** 服务开始时间 */
    private LocalDateTime startedAt;

    /** 服务结束时间 */
    private LocalDateTime endedAt;

    /** 结束原因：TIMEOUT-超时自动结束 */
    private String endReason;

    /** 最后一条消息时间（超时检测基准） */
    private LocalDateTime lastMessageTime;

    /** 是否已发送提前结束通知（0未发 1已发，防重复提示） */
    private Integer notifiedBeforeEnd;

    /** 评分 1-5（评价后非空） */
    private Integer rating;

    /** 评价标签（逗号分隔） */
    private String tags;

    /** 评价留言 */
    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
