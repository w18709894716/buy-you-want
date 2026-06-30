package com.byw.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.product.entity.Category;
import com.byw.product.mapper.CategoryMapper;
import com.byw.product.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> getCategoryTree() {
        // 返回全部分类，由前端构建为树结构
        return baseMapper.selectList(null);
    }
}
