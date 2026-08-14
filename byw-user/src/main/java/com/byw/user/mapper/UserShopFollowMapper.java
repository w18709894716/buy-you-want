package com.byw.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.byw.user.entity.UserShopFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserShopFollowMapper extends BaseMapper<UserShopFollow> {

    /** 查询关注记录（包含已逻辑删除的记录，绕过 @TableLogic，用于取消后重新关注时恢复） */
    @Select("SELECT * FROM t_user_shop_follow WHERE user_id = #{userId} AND shop_id = #{shopId} LIMIT 1")
    UserShopFollow selectByUserAndShopIncludeDeleted(@Param("userId") Long userId,
                                                     @Param("shopId") Long shopId);

    /** 恢复已软删除的关注记录 */
    @Update("UPDATE t_user_shop_follow SET deleted = 0, created_at = NOW(), updated_at = NOW() WHERE id = #{id}")
    int restoreById(@Param("id") Long id);
}
