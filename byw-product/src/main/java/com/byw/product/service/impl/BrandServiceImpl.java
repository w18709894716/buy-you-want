package com.byw.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.product.entity.Brand;
import com.byw.product.mapper.BrandMapper;
import com.byw.product.service.BrandService;
import org.springframework.stereotype.Service;

@Service
public class BrandServiceImpl extends ServiceImpl<BrandMapper, Brand> implements BrandService {
}
