package com.byw.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ParameterUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.sql.SQLException;
import java.time.LocalDateTime;

@Configuration
public class MyBatisPlusConfig implements MetaObjectHandler {

    /** 单页最大条数：分页的硬上限 */
    private static final long MAX_PAGE_SIZE = 100L;

    /** 单页默认条数：pageSize 缺失或非法时使用 */
    private static final long DEFAULT_PAGE_SIZE = 10L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 归一化必须排在分页插件之前：MP 对 size < 0 会直接跳过分页（SQL 不带 LIMIT，等于把整表返回）
        interceptor.addInnerInterceptor(new PageParamNormalizer());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 分页参数归一化：把 pageNum / pageSize 收敛到合法区间。
     *
     * <p>原先前端传 {@code pageSize=-1} 或 {@code 0} 时，MyBatis-Plus 会认为"不需要分页"，
     * 生成的 SQL 不带 LIMIT —— 结果不是"取太多"，而是把**整张表**返回给调用方；
     * 传超大 pageSize 则靠分页插件的 maxLimit 兜底。这里把两种都收敛掉，
     * 使得限流/校验不必依赖每个接口自己记得写。
     */
    static class PageParamNormalizer implements InnerInterceptor {

        @Override
        public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                                ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
            ParameterUtils.findPage(parameter).ifPresent(PageParamNormalizer::normalize);
        }

        private static void normalize(IPage<?> page) {
            if (page.getCurrent() < 1) {
                page.setCurrent(1);
            }
            long size = page.getSize();
            if (size < 1) {
                page.setSize(DEFAULT_PAGE_SIZE);
            } else if (size > MAX_PAGE_SIZE) {
                page.setSize(MAX_PAGE_SIZE);
            }
        }
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "deleted", () -> 0, Integer.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
    }
}
