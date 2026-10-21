package com.byw.product.service.impl;

import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.anno.SerialPolicy;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.byw.common.core.constant.CacheNames;
import com.byw.product.entity.Category;
import com.byw.product.mapper.CategoryMapper;
import com.byw.product.service.CategoryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {

    @Override
    @Cached(name = CacheNames.CATEGORY_TREE, key = "'all'", expire = 2, timeUnit = TimeUnit.HOURS,
            serialPolicy = SerialPolicy.JAVA)
    public List<Category> getCategoryTree() {
        // 返回全部分类，由前端构建为树结构
        return baseMapper.selectList(null);
    }
}
