package com.byw.im.task;

import com.byw.im.service.ServiceRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * IM 服务超时扫描：双方（用户与客服）无互发消息超过超时阈值自动结束服务。
 * 提前 warningAhead 分钟广播"即将自动结束"提示（每服务仅一次）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTimeoutScheduler {

    private final ServiceRecordService serviceRecordService;

    /** 服务超时阈值（分钟），默认 10 */
    @Value("${byw.im.service-timeout-minutes:10}")
    private int timeoutMinutes;

    /** 提前结束通知的提前量（分钟），默认 3 */
    @Value("${byw.im.service-warning-ahead-minutes:3}")
    private int aheadMinutes;

    /**
     * 每 30 秒扫描一次进行中的服务。
     * 幂等：scanTimeoutServices 内部按状态与通知标记推进，重复执行不重复结束/提示。
     */
    @Scheduled(fixedDelayString = "${byw.im.service-scan-interval-ms:30000}")
    public void scanTimeoutServices() {
        try {
            LocalDateTime now = LocalDateTime.now();
            serviceRecordService.scanTimeoutServices(
                    now.minusMinutes(Math.max(timeoutMinutes - aheadMinutes, 1)),
                    now.minusMinutes(timeoutMinutes));
        } catch (Exception e) {
            log.error("IM 服务超时扫描任务执行失败", e);
        }
    }
}
