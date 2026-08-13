package com.byw.im.dto;

import lombok.Data;

import java.util.List;

/**
 * C 端 FAQ 引导选项聚合：FAQ 列表 + 店铺分流引导信息。
 * <ul>
 *   <li>inServiceTime=false：非服务时间模式（机器人默认打开），ChatPanel 展示 FAQ 引导</li>
 *   <li>robotFirst=true：服务时间内优先智能机器人，ChatPanel 同样展示 FAQ 引导</li>
 *   <li>offHoursTip：非服务时间提示语（未配置时为默认文案）</li>
 * </ul>
 */
@Data
public class FaqOptionsDTO {

    /** 启用中的 FAQ 引导列表 */
    private List<FaqOptionDTO> faqs;

    /** 服务时间内是否优先智能机器人（第一个服务时间内的启用规则决定） */
    private boolean robotFirst;

    /** 当前是否在服务时间内（false=非服务时间模式） */
    private boolean inServiceTime;

    /** 非服务时间提示语（非服务时间模式时非空） */
    private String offHoursTip;
}
