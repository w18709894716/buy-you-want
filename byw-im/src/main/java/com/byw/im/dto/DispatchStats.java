package com.byw.im.dto;

import lombok.Data;

import java.util.Map;

/**
 * 分流统计（商家端分流页顶部统计条）：
 * 排队总数/按组分、在线客服总数/按组分、挂起客服总数/按组分、离线消息池总数。
 */
@Data
public class DispatchStats {

    /** 排队中会话总数 */
    private long queueTotal;

    /** 排队中会话按分组统计（groupId → 数量；未入组会话不入此 Map） */
    private Map<Long, Long> queueByGroup;

    /** 本店在线客服总数 */
    private long onlineTotal;

    /** 本店在线客服按分组统计（groupId → 数量） */
    private Map<Long, Long> onlineByGroup;

    /** 本店挂起客服总数 */
    private long suspendedTotal;

    /** 本店挂起客服按分组统计（groupId → 数量） */
    private Map<Long, Long> suspendedByGroup;

    /** 离线消息池会话总数 */
    private long offlinePoolTotal;
}
