package com.byw.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.user.entity.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserFavoriteMapper extends BaseMapper<UserFavorite> {

    /** 查询收藏记录（包含已逻辑删除的记录，绕过 @TableLogic，用于取消后重新收藏时恢复） */
    @Select("SELECT * FROM t_user_favorite WHERE user_id = #{userId} AND product_id = #{productId} LIMIT 1")
    UserFavorite selectByUserAndProductIncludeDeleted(@Param("userId") Long userId,
                                                      @Param("productId") Long productId);

    /** 恢复已软删除的收藏记录 */
    @Update("UPDATE t_user_favorite SET deleted = 0, created_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
