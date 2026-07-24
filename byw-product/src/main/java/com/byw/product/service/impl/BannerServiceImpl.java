package com.byw.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.product.entity.Banner;
import com.byw.product.mapper.BannerMapper;
import com.byw.product.service.BannerService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BannerServiceImpl extends ServiceImpl<BannerMapper, Banner> implements BannerService {

    @Override
    public List<Banner> listActiveBanners() {
        return listActiveBanners(null);
    }

    @Override
    public List<Banner> listActiveBanners(Integer position) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Banner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Banner::getStatus, 1)
                .eq(position != null, Banner::getPosition, position)
                // 已到上线时间（start_time 为空表示立即上线）
                .and(w -> w.isNull(Banner::getStartTime).or().le(Banner::getStartTime, now))
                // 未到下线时间（end_time 为空表示永久有效）
                .and(w -> w.isNull(Banner::getEndTime).or().ge(Banner::getEndTime, now))
                .orderByAsc(Banner::getSortOrder)
                .orderByDesc(Banner::getId);
        return list(wrapper);
    }
}
