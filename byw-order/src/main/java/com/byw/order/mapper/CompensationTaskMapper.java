package com.byw.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.order.entity.CompensationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompensationTaskMapper extends BaseMapper<CompensationTask> {

    /**
     * 认领一条待执行任务：0→3。返回 1 表示抢到。
     *
     * <p>用 CAS 而不是"先查后改"：事务提交后的立即驱动与定时扫描可能同时碰到同一条，
     * 只有 CAS 成功的一方允许执行，另一方静默让出。
     */
    @Update("UPDATE t_compensation_task SET status = 3, updated_at = NOW() "
            + "WHERE id = #{id} AND status = 0")
    int claim(@Param("id") Long id);

    /** 到期待执行的任务（status=0 且 next_retry_at 已到） */
    @Select("SELECT * FROM t_compensation_task "
            + "WHERE status = 0 AND next_retry_at <= #{now} "
            + "ORDER BY next_retry_at LIMIT #{limit}")
    List<CompensationTask> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 回写执行结果（成功 / 待重试 / 需人工） */
    @Update("UPDATE t_compensation_task SET status = #{status}, retry_count = #{retryCount}, "
            + "next_retry_at = #{nextRetryAt}, last_error = #{lastError}, updated_at = NOW() "
            + "WHERE id = #{id}")
    int updateAfterRun(@Param("id") Long id,
                       @Param("status") int status,
                       @Param("retryCount") int retryCount,
                       @Param("nextRetryAt") LocalDateTime nextRetryAt,
                       @Param("lastError") String lastError);

    /**
     * 回收卡在"执行中"的任务：执行方在「认领成功、写回结果之前」退出（进程被杀、重启）时，
     * 任务会永远停在 3。超过阈值仍未写回，说明那次执行没有走完，放回待执行重来一次。
     * 各个执行体都是幂等的，重放安全。
     */
    @Update("UPDATE t_compensation_task SET status = 0, updated_at = NOW() "
            + "WHERE status = 3 AND updated_at < #{before}")
    int requeueStale(@Param("before") LocalDateTime before);
}
