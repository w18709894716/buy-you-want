package com.byw.im.dto;

import com.byw.im.entity.DispatchRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分流规则解析结果：供 ImService 分流决策树使用。
 * <ul>
 *   <li>inServiceTime=false：非服务时间模式（全部启用规则均不在服务时间内），不分配不进队列不进池</li>
 *   <li>repeatStaffId 非空：回头客命中，直接分配给该客服（无视挂起/最大接待数，仅要求在线）</li>
 *   <li>groupId 非空：命中规则，进入绑定分组按权重分配</li>
 *   <li>rule/groupId 均为 null 且 inServiceTime=true：基础分流（全店在线未挂起均衡分配）</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DispatchResolveResult {

    /** 命中的规则（基础分流/非服务时间模式为 null） */
    private DispatchRule rule;

    /** 命中规则绑定的分组ID（未命中为 null） */
    private Long groupId;

    /** 是否在服务时间内（false=非服务时间模式） */
    private boolean inServiceTime;

    /** 非服务时间提示语（未配置取默认文案） */
    private String offHoursTip;

    /** 服务时间内是否优先智能机器人（取自第一个服务时间内的启用规则） */
    private boolean robotFirst;

    /** 回头客：窗口内最近接待过该用户且在线的客服ID；未命中为 null */
    private Long repeatStaffId;
}
