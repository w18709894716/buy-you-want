package com.byw.product.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.product.entity.Category;
import com.byw.product.mapper.CategoryMapper;
import com.byw.product.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    public List<Category> getCategoryTree() {
        List<Category> allCategories = baseMapper.selectList(null);
        // Return root categories (parentId == 0), the frontend can build the tree
        return allCategories.stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0)
                .collect(Collectors.toList());
    }
}
