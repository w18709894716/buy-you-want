package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.product.entity.Brand;

import java.util.List;

public interface BrandService extends IService<Brand> {

    /** 用户端全量品牌列表（按 sortOrder 升序），走缓存 */
    List<Brand> listAllBrands();
}
