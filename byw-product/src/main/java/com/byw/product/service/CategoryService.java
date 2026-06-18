package com.byw.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.byw.product.entity.Category;

import java.util.List;

public interface CategoryService extends IService<Category> {

    List<Category> getCategoryTree();
}
