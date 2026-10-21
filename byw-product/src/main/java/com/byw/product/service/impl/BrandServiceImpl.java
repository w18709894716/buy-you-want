package com.byw.product.service.impl;

import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.anno.SerialPolicy;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.core.constant.CacheNames;
import com.byw.product.entity.Brand;
import com.byw.product.mapper.BrandMapper;
import com.byw.product.service.BrandService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements BrandService {

    @Override
    @Cached(name = CacheNames.BRAND_ALL, key = "'all'", expire = 2, timeUnit = TimeUnit.HOURS,
            serialPolicy = SerialPolicy.JAVA)
    public List<Brand> listAllBrands() {
        return list(new LambdaQueryWrapper<Brand>().orderByAsc(Brand::getSortOrder));
    }
}
