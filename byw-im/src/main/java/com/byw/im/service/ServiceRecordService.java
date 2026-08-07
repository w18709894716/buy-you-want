package com.byw.im.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.byw.im.entity.ServiceRecord;

import java.time.LocalDateTime;

/**
 * IM 服务记录服务：一次服务 = 一次评价单元。
 * 服务生命周期由 ImService 在接入/转接/发消息/掉线等节点调用本服务维护；
 * 超时自动结束由定时任务驱动；评价能力替代原 SatisfactionService（t_im_satisfaction 废弃）。
 */
public interface ServiceRecordService {

    String STATUS_IN_PROGRESS = "IN_PROGRESS";
    String STATUS_ENDED = "ENDED";
    String STATUS_RATED = "RATED";

    /** 结束原因：超时自动结束 */
    String END_REASON_TIMEOUT = "TIMEOUT";

    /**
     * 保活/创建进行中服务：无 IN_PROGRESS 服务则创建（服务开始），有则刷新最后消息时间。
     * staff 解析优先级：传入 staffId &gt; 会话当前接待客服 &gt; 空（待分配）。
     * 注意：不覆盖已存在的最终处理人（转接/掉线重分配请用 updateFinalStaff）。
     */
    ServiceRecord touchActive(Long conversationId, Long staffId, String staffName);

    /**
     * 更新进行中服务的最终处理人（转接/接管/掉线后重新分配时调用；介入不调用）。
     * 无进行中服务则创建并直接指定处理人。
     */
    void updateFinalStaff(Long conversationId, Long staffId, String staffName);

    /**
     * 超时扫描（定时任务驱动）：
     * 1) 最后消息时间早于 warningThreshold 且未通知过 → 广播"即将自动结束"提示并标记；
     * 2) 最后消息时间早于 endThreshold → 结束服务（ENDED），广播"本次服务已结束"。
     * 最后消息时间缺失时以服务开始时间计。
     */
    void scanTimeoutServices(LocalDateTime warningThreshold, LocalDateTime endThreshold);

    /** 当前会话是否已有进行中服务（掉线重分配/超时扫描前置判断） */
    boolean hasActive(Long conversationId);

    /** 当前会话最近一条可评价服务（ENDED 且未评价且有最终处理人）；无则 null */
    ServiceRecord latestRatable(Long conversationId, Long userId);

    /**
     * 提交评价：对可评价服务写入评分并置 RATED。
     * 幂等：重复提交（已 RATED）返回已有记录；无可评价服务抛 IllegalArgumentException。
     */
    ServiceRecord submitRating(Long conversationId, Long userId, Long shopId,
                               Integer rating, String tags, String comment);

    /** 商家分页查询本店已评价记录 */
    IPage<ServiceRecord> listByShop(Long shopId, Integer page, Integer pageSize);

    /** 商家评分统计（仅统计已评价记录） */
    SatisfactionService.SatisfactionStats stats(Long shopId);
}
