package com.byw.common.core.constant;

/**
 * JetCache 缓存名。最终 Redis key = jetcache.remote.default.keyPrefix + name + 转换后的 key。
 *
 * <p>返回值是 List 的缓存必须显式指定 {@code serialPolicy = SerialPolicy.JAVA}：
 * 全局的 FASTJSON2 只把运行时类名（ArrayList）写进缓存，反序列化时拿不到元素泛型，
 * 取出来会是 JSONObject 而不是实体，遍历赋值时抛 ClassCastException。
 * 单个实体没有这个问题，用全局的 FASTJSON2 即可。
 */
public class CacheNames {

    public static final String PRODUCT_DETAIL = "product:detail:";

    public static final String CATEGORY_TREE = "category:tree:";

    public static final String BRAND_ALL = "brand:all:";

    public static final String SHOP_INFO = "shop:info:";

    public static final String USER_INFO = "user:info:";

    private CacheNames() {
    }
}
