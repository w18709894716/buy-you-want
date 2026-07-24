package com.byw.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.product.ProductFeignClient;
import com.byw.api.product.dto.ProductDTO;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.api.user.UserFeignClient;
import com.byw.api.user.dto.UserDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
import com.byw.common.core.result.R;
import com.byw.review.document.ReviewDetail;
import com.byw.review.entity.Review;
import com.byw.review.entity.ReviewImage;
import com.byw.review.mapper.ReviewImageMapper;
import com.byw.review.mapper.ReviewMapper;
import com.byw.review.repository.ReviewDetailRepository;
import com.byw.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final ReviewImageMapper reviewImageMapper;
    private final ReviewDetailRepository reviewDetailRepository;
    private final MongoTemplate mongoTemplate;
    private final OrderFeignClient orderFeignClient;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createReview(ReviewDetail reviewDetail, Integer rating, String content, Integer isAnonymous) {
        // 1. 检查是否已评价
        if (reviewExists(reviewDetail.getOrderId())) {
            throw new BusinessException("该订单已评价");
        }

        // 2. 保存到MySQL
        Review review = new Review();
        review.setOrderNo(reviewDetail.getOrderId());
        review.setUserId(reviewDetail.getUserId());
        review.setProductId(reviewDetail.getProductId());
        review.setRating(rating);
        review.setContent(content);
        review.setIsAnonymous(isAnonymous != null ? isAnonymous : 0);
        review.setHasImage(reviewDetail.getImages() != null && !reviewDetail.getImages().isEmpty() ? 1 : 0);
        review.setStatus(1); // 显示
        reviewMapper.insert(review);

        // 3. 保存图片到MySQL
        if (reviewDetail.getImages() != null) {
            for (String imageUrl : reviewDetail.getImages()) {
                ReviewImage image = new ReviewImage();
                image.setReviewId(review.getId());
                image.setImageUrl(imageUrl);
                image.setType(0); // 初评
                reviewImageMapper.insert(image);
            }
        }

        // 4. 保存到MongoDB
        reviewDetail.setCreatedAt(LocalDateTime.now());
        reviewDetail.setIsAnonymous(isAnonymous != null ? isAnonymous : 0);
        reviewDetail.setStatus(1); // 显示，与MySQL Review.status同步
        reviewDetailRepository.save(reviewDetail);

        // 5. 更新订单评价状态
        try {
            orderFeignClient.updateReviewed(reviewDetail.getOrderId(), 1);
        } catch (Exception e) {
            log.warn("更新订单评价状态失败: orderNo={}, error={}", reviewDetail.getOrderId(), e.getMessage());
        }

        log.info("评价创建成功: orderNo={}, productId={}", reviewDetail.getOrderId(), reviewDetail.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendReview(String orderNo, Long userId, Long skuId, String appendContent, List<String> appendImages) {
        // 1. 查找该商品的原评价（按 orderNo+userId+skuId 精确定位，避免一单多商品时命中多条）
        Review review = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderNo, orderNo)
                        .eq(Review::getUserId, userId)
                        .eq(Review::getSkuId, skuId));
        if (review == null) {
            throw new BusinessException("评价不存在");
        }

        // 2. 查找评价明细，校验是否已追评（每个商品仅允许追评一次）
        Query query = new Query(Criteria.where("orderId").is(orderNo)
                .and("userId").is(userId).and("skuId").is(skuId));
        ReviewDetail detail = mongoTemplate.findOne(query, ReviewDetail.class);
        if (detail != null && detail.getAppendContent() != null && !detail.getAppendContent().isBlank()) {
            throw new BusinessException("该商品已追评，不能重复追评");
        }

        // 3. 保存追评图片到MySQL
        if (appendImages != null) {
            for (String imageUrl : appendImages) {
                ReviewImage image = new ReviewImage();
                image.setReviewId(review.getId());
                image.setImageUrl(imageUrl);
                image.setType(1); // 追评
                reviewImageMapper.insert(image);
            }
        }

        // 4. 更新MongoDB
        if (detail != null) {
            detail.setAppendContent(appendContent);
            detail.setAppendImages(appendImages);
            reviewDetailRepository.save(detail);
        }

        // 5. 仅当本人该订单全部已评商品都已追评时，才将订单标记为已追评(2)，避免一单多商品时提前隐藏追评入口
        try {
            List<ReviewDetail> all = mongoTemplate.find(
                    new Query(Criteria.where("orderId").is(orderNo).and("userId").is(userId)),
                    ReviewDetail.class);
            boolean allAppended = !all.isEmpty() && all.stream()
                    .allMatch(rd -> rd.getAppendContent() != null && !rd.getAppendContent().isBlank());
            if (allAppended) {
                orderFeignClient.updateReviewed(orderNo, 2);
            }
        } catch (Exception e) {
            log.warn("更新订单追评状态失败: orderNo={}, error={}", orderNo, e.getMessage());
        }

        log.info("追评成功: orderNo={}, skuId={}", orderNo, skuId);
    }

    @Override
    public PageResult<ReviewDetail> getReviewsByProduct(Long productId, Integer rating, Boolean hasImage,
                                                         Integer pageNum, Integer pageSize) {
        Criteria criteria = Criteria.where("productId").is(productId);
        if (rating != null) {
            criteria = criteria.and("rating").is(rating);
        }
        if (Boolean.TRUE.equals(hasImage)) {
            // 数组首元素存在即非空，避免对同一字段重复添加 Criteria
            criteria = criteria.and("images.0").exists(true);
        }
        // 只展示未被隐藏的评价（status!=0，兼容旧数据 status 字段缺失的情况）
        criteria = criteria.and("status").ne(0);

        long total = mongoTemplate.count(new Query(criteria), ReviewDetail.class);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip((long) (pageNum - 1) * pageSize)
                .limit(pageSize);

        List<ReviewDetail> list = mongoTemplate.find(query, ReviewDetail.class);

        return PageResult.of(list, total, pageNum, pageSize);
    }

    @Override
    public ReviewStatsDTO getReviewStats(Long productId) {
        // 使用MongoDB聚合查询统计
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("productId").is(productId)),
                Aggregation.group("rating").count().as("count")
        );

        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, ReviewDetail.class, Map.class);
        List<Map> mappedResults = results.getMappedResults();

        ReviewStatsDTO stats = new ReviewStatsDTO();
        stats.setProductId(productId);

        int totalCount = 0;
        int totalRating = 0;
        int fiveStar = 0, fourStar = 0, threeStar = 0, twoStar = 0, oneStar = 0;

        for (Map map : mappedResults) {
            Object idObj = map.get("_id");
            Object countObj = map.get("count");
            if (idObj == null || countObj == null) {
                continue; // 跳过 rating 为空的文档，避免拆箱 NPE
            }
            int r = ((Number) idObj).intValue();
            int count = ((Number) countObj).intValue();
            totalCount += count;
            totalRating += r * count;

            switch (r) {
                case 5 -> fiveStar = count;
                case 4 -> fourStar = count;
                case 3 -> threeStar = count;
                case 2 -> twoStar = count;
                case 1 -> oneStar = count;
                default -> { }
            }
        }

        stats.setTotalCount(totalCount);
        stats.setAvgRating(totalCount > 0 ? Math.round((double) totalRating / totalCount * 10.0) / 10.0 : 0.0);
        stats.setFiveStar(fiveStar);
        stats.setFourStar(fourStar);
        stats.setThreeStar(threeStar);
        stats.setTwoStar(twoStar);
        stats.setOneStar(oneStar);

        // 统计有图评价数（数组首元素存在即非空）
        long withImageCount = mongoTemplate.count(
                new Query(Criteria.where("productId").is(productId)
                        .and("images.0").exists(true)),
                ReviewDetail.class);
        stats.setWithImageCount((int) withImageCount);

        return stats;
    }

    @Override
    public boolean reviewExists(String orderNo) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderNo, orderNo)) > 0;
    }

    @Override
    public PageResult<Map<String, Object>> adminListReviews(Integer pageNum, Integer pageSize, Integer rating, Integer status) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(rating != null, Review::getRating, rating)
                .eq(status != null, Review::getStatus, status)
                .orderByDesc(Review::getCreatedAt);
        Page<Review> page = new Page<>(pageNum, pageSize);
        Page<Review> reviewPage = reviewMapper.selectPage(page, wrapper);

        // 同页内相同商品仅查询一次名称
        Map<Long, String> productNameCache = new HashMap<>();
        // 同页内相同用户仅查询一次名称
        Map<Long, String> userNameCache = new HashMap<>();
        List<Map<String, Object>> list = reviewPage.getRecords().stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("orderNo", r.getOrderNo());
            map.put("userId", r.getUserId());
            map.put("username", resolveUserName(r.getUserId(), userNameCache));
            map.put("productId", r.getProductId());
            map.put("productName", resolveProductName(r.getProductId(), productNameCache));
            map.put("rating", r.getRating());
            map.put("content", r.getContent());
            map.put("visible", r.getStatus() != null && r.getStatus() == 1);
            map.put("created", r.getCreatedAt() != null ? r.getCreatedAt().toString() : "");

            // 从 MongoDB 评价明细补充图片与追评信息
            ReviewDetail detail = findReviewDetail(r);
            List<String> images = detail != null ? detail.getImages() : null;
            String appendContent = detail != null ? detail.getAppendContent() : null;
            List<String> appendImages = detail != null ? detail.getAppendImages() : null;
            map.put("images", images != null ? images : Collections.emptyList());
            map.put("hasImage", images != null && !images.isEmpty());
            map.put("appendContent", appendContent);
            map.put("appendImages", appendImages != null ? appendImages : Collections.emptyList());
            map.put("hasAppend", appendContent != null && !appendContent.isBlank());
            return map;
        }).collect(Collectors.toList());

        return PageResult.of(list, reviewPage.getTotal(), pageNum, pageSize);
    }

    /**
     * 查询商品名称，结果缓存到 cache 避免同页重复远程调用；失败时降级为 商品#{id}
     */
    private String resolveProductName(Long productId, Map<Long, String> cache) {
        if (productId == null) {
            return "";
        }
        return cache.computeIfAbsent(productId, pid -> {
            try {
                R<ProductDTO> resp = productFeignClient.getProductById(pid);
                if (resp != null && resp.getData() != null && resp.getData().getName() != null) {
                    return resp.getData().getName();
                }
            } catch (Exception e) {
                log.warn("获取商品名称失败: productId={}, error={}", pid, e.getMessage());
            }
            return "商品#" + pid;
        });
    }

    /**
     * 查询用户展示名（昵称优先，其次用户名），结果缓存到 cache 避免同页重复远程调用；
     * 失败或为空时降级为 用户{id}，保证非空可追溯。管理端展示真实名，不做脱敏。
     */
    private String resolveUserName(Long userId, Map<Long, String> cache) {
        if (userId == null) {
            return "";
        }
        return cache.computeIfAbsent(userId, uid -> {
            try {
                R<UserDTO> resp = userFeignClient.getUserById(uid);
                if (resp != null && resp.isSuccess() && resp.getData() != null) {
                    UserDTO u = resp.getData();
                    if (u.getNickname() != null && !u.getNickname().isBlank()) {
                        return u.getNickname();
                    }
                    if (u.getUsername() != null && !u.getUsername().isBlank()) {
                        return u.getUsername();
                    }
                }
            } catch (Exception e) {
                log.warn("获取用户名称失败: userId={}, error={}", uid, e.getMessage());
            }
            return "用户" + uid;
        });
    }

    /**
     * 定位评价对应的 MongoDB 明细查询：优先按 orderId+userId+skuId，skuId 为空时回退按 productId
     */
    private Query buildDetailQuery(Review r) {
        Criteria criteria = Criteria.where("orderId").is(r.getOrderNo())
                .and("userId").is(r.getUserId());
        if (r.getSkuId() != null) {
            criteria = criteria.and("skuId").is(r.getSkuId());
        } else {
            criteria = criteria.and("productId").is(r.getProductId());
        }
        return new Query(criteria);
    }

    private ReviewDetail findReviewDetail(Review r) {
        try {
            return mongoTemplate.findOne(buildDetailQuery(r), ReviewDetail.class);
        } catch (Exception e) {
            log.warn("查询评价明细失败: reviewId={}, error={}", r.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminUpdateVisible(Long reviewId, Integer status) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        review.setStatus(status);
        reviewMapper.updateById(review);
        // 同步到 MongoDB 明细，保证 C 端商品评价列表同步隐藏/显示
        try {
            mongoTemplate.updateMulti(buildDetailQuery(review), new Update().set("status", status), ReviewDetail.class);
        } catch (Exception e) {
            log.warn("同步评价显示状态到MongoDB失败: reviewId={}, error={}", reviewId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteReview(Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        reviewMapper.deleteById(reviewId);
        // 同步删除 MongoDB 明细与图片，避免 C 端仍展示已删评价
        try {
            mongoTemplate.remove(buildDetailQuery(review), ReviewDetail.class);
            reviewImageMapper.delete(new LambdaQueryWrapper<ReviewImage>()
                    .eq(ReviewImage::getReviewId, reviewId));
        } catch (Exception e) {
            log.warn("同步删除MongoDB评价明细失败: reviewId={}, error={}", reviewId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createBatchReviews(Long userId, String orderNo, List<ReviewDetail> reviewDetails) {
        // 1. 检查是否已评价
        if (reviewExists(orderNo)) {
            throw new BusinessException("该订单已评价");
        }

        for (ReviewDetail detail : reviewDetails) {
            detail.setOrderId(orderNo);
            detail.setUserId(userId);

            // 2. 保存到MySQL
            Review review = new Review();
            review.setOrderNo(orderNo);
            review.setUserId(userId);
            review.setProductId(detail.getProductId());
            review.setSkuId(detail.getSkuId());
            review.setRating(detail.getRating());
            review.setContent(detail.getContent());
            review.setIsAnonymous(detail.getIsAnonymous() != null ? detail.getIsAnonymous() : 0);
            review.setHasImage(detail.getImages() != null && !detail.getImages().isEmpty() ? 1 : 0);
            review.setStatus(1);
            reviewMapper.insert(review);

            // 3. 保存图片
            if (detail.getImages() != null) {
                for (String imageUrl : detail.getImages()) {
                    ReviewImage image = new ReviewImage();
                    image.setReviewId(review.getId());
                    image.setImageUrl(imageUrl);
                    image.setType(0);
                    reviewImageMapper.insert(image);
                }
            }

            // 4. 保存到MongoDB
            detail.setCreatedAt(LocalDateTime.now());
            detail.setStatus(1); // 显示，与MySQL Review.status同步
            reviewDetailRepository.save(detail);
        }

        // 5. 更新订单评价状态
        try {
            orderFeignClient.updateReviewed(orderNo, 1);
        } catch (Exception e) {
            log.warn("更新订单评价状态失败: orderNo={}, error={}", orderNo, e.getMessage());
        }

        log.info("批量评价创建成功: orderNo={}, count={}", orderNo, reviewDetails.size());
    }

    @Override
    public PageResult<Map<String, Object>> getUserReviews(Long userId, Integer pageNum, Integer pageSize, Boolean hasImage) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId);
        if (Boolean.TRUE.equals(hasImage)) {
            wrapper.eq(Review::getHasImage, 1);
        }
        wrapper.orderByDesc(Review::getCreatedAt);
        Page<Review> page = new Page<>(pageNum, pageSize);
        Page<Review> reviewPage = reviewMapper.selectPage(page, wrapper);

        List<Map<String, Object>> list = reviewPage.getRecords().stream().map(r -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", r.getId());
            map.put("orderNo", r.getOrderNo());
            map.put("productId", r.getProductId());
            map.put("skuId", r.getSkuId());
            map.put("rating", r.getRating());
            map.put("content", r.getContent());
            map.put("isAnonymous", r.getIsAnonymous());
            map.put("status", r.getStatus());
            map.put("createdAt", r.getCreatedAt());

            // 从 MongoDB 评价明细补充图片与追评信息
            ReviewDetail detail = findReviewDetail(r);
            List<String> images = detail != null ? detail.getImages() : null;
            String appendContent = detail != null ? detail.getAppendContent() : null;
            List<String> appendImages = detail != null ? detail.getAppendImages() : null;
            map.put("images", images != null ? images : Collections.emptyList());
            map.put("hasImage", images != null && !images.isEmpty());
            map.put("appendContent", appendContent);
            map.put("appendImages", appendImages != null ? appendImages : Collections.emptyList());
            map.put("hasAppend", appendContent != null && !appendContent.isBlank());
            return map;
        }).collect(Collectors.toList());

        return PageResult.of(list, reviewPage.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<ReviewDetail> getOrderReviews(String orderNo, Long userId) {
        Query query = new Query(Criteria.where("orderId").is(orderNo).and("userId").is(userId));
        return mongoTemplate.find(query, ReviewDetail.class);
    }
}
