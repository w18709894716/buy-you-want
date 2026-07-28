package com.byw.settle.service;

import com.byw.api.settle.dto.WithdrawApplyDTO;
import com.byw.api.settle.dto.WithdrawRecordDTO;
import com.byw.common.core.result.PageResult;

/**
 * 提现服务：商家发起提现（冻结可用余额）、平台审批（通过打款/驳回解冻）、提现单查询。
 */
public interface WithdrawService {

    /** 商家发起提现：校验可用余额→冻结→生成待审核提现单，返回提现单号。 */
    String applyWithdraw(Long shopId, WithdrawApplyDTO applyDTO);

    /** 提现单分页查询（shopId 为空表示平台查全部）。 */
    PageResult<WithdrawRecordDTO> listWithdraws(Long shopId, Integer status, Integer pageNum, Integer pageSize);

    /** 平台审批：pass=true 通过打款(冻结→已提现)；pass=false 驳回(冻结→可用)。 */
    boolean auditWithdraw(Long withdrawId, Boolean pass, String rejectReason, String auditor);
}
