package com.byw.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.byw.api.order.OrderFeignClient;
import com.byw.api.review.dto.ReviewStatsDTO;
import com.byw.common.core.exception.BusinessException;
import com.byw.common.core.result.PageResult;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
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
    public PageResult<Review> adminListReviews(Integer pageNum, Integer pageSize, Integer rating, Integer status) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(rating != null, Review::getRating, rating)
                .eq(status != null, Review::getStatus, status)
                .orderByDesc(Review::getCreatedAt);
        Page<Review> page = new Page<>(pageNum, pageSize);
        Page<Review> reviewPage = reviewMapper.selectPage(page, wrapper);
        return PageResult.of(reviewPage.getRecords(), reviewPage.getTotal(), pageNum, pageSize);
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
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminDeleteReview(Long reviewId) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BusinessException("评价不存在");
        }
        reviewMapper.deleteById(reviewId);
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
    public PageResult<Review> getUserReviews(Long userId, Integer pageNum, Integer pageSize, Boolean hasImage) {
        LambdaQueryWrapper<Review> wrapper = new LambdaQueryWrapper<Review>()
                .eq(Review::getUserId, userId);
        if (Boolean.TRUE.equals(hasImage)) {
            wrapper.eq(Review::getHasImage, 1);
        }
        wrapper.orderByDesc(Review::getCreatedAt);
        Page<Review> page = new Page<>(pageNum, pageSize);
        Page<Review> reviewPage = reviewMapper.selectPage(page, wrapper);
        return PageResult.of(reviewPage.getRecords(), reviewPage.getTotal(), pageNum, pageSize);
    }

    @Override
    public List<ReviewDetail> getOrderReviews(String orderNo, Long userId) {
        Query query = new Query(Criteria.where("orderId").is(orderNo).and("userId").is(userId));
        return mongoTemplate.find(query, ReviewDetail.class);
    }
}
