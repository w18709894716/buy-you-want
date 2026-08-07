package com.byw.im.dto;

import lombok.Data;

/**
 * C 端 FAQ 引导选项：仅暴露问题文本，答案由用户点击后机器人回复下发。
 */
@Data
public class FaqOptionDTO {

    /** FAQ ID */
    private Long id;

    /** 问题文本 */
    private String question;
}
