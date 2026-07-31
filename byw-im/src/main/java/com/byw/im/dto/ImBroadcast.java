package com.byw.im.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 跨节点广播事件：消息落库后经 RocketMQ 广播 Topic IM_MESSAGE 投递，
 * 所有 byw-im 节点消费后，向本节点持有的 userPrincipal(userId) / shopPrincipal(shopId) 会话下推。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImBroadcast {

    /** message 消息 / typing 正在输入 / read 已读回执 */
    private String action;
    /** 会话买家ID，用于定位买家端 principal */
    private Long userId;
    /** 会话店铺ID，用于定位商家端 principal */
    private Long shopId;
    /** 下推给客户端的 data 负载（MessageView 或信令 Map） */
    private Object data;
}
