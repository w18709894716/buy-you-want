package com.byw.im.dto;

import lombok.Data;

/**
 * 客服简要信息（转接选人列表用）。
 */
@Data
public class StaffBriefDTO {

    /** 客服ID（merchant_account.id） */
    private Long id;

    /** 客服姓名（真实姓名优先，兜底登录名/ID） */
    private String name;
}
