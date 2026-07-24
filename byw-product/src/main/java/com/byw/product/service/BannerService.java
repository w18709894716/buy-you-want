package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.product.entity.Banner;

import java.util.List;

public interface BannerService extends IService<Banner> {

    /**
     * 获取当前生效的轮播图列表
     * 条件：已启用 且 处于上线时间窗口内（start_time/end_time 为空表示不限制）
     * 定时上下线由查询时按时间过滤实现，无需定时任务
     */
    List<Banner> listActiveBanners();

    /**
     * 按展示位置获取当前生效的列表
     * @param position 0轮播 1右侧活动位；为 null 时返回全部生效项
     */
    List<Banner> listActiveBanners(Integer position);
}
