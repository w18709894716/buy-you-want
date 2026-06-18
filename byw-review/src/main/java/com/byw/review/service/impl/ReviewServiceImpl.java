package com.byw.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
        reviewDetailRepository.save(reviewDetail);

        log.info("评价创建成功: orderNo={}, productId={}", reviewDetail.getOrderId(), reviewDetail.getProductId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void appendReview(String orderNo, Long userId, String appendContent, List<String> appendImages) {
        // 1. 查找原评价
        Review review = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getOrderNo, orderNo)
                        .eq(Review::getUserId, userId));
        if (review == null) {
            throw new BusinessException("评价不存在");
        }

        // 2. 保存追评图片到MySQL
        if (appendImages != null) {
            for (String imageUrl : appendImages) {
                ReviewImage image = new ReviewImage();
                image.setReviewId(review.getId());
                image.setImageUrl(imageUrl);
                image.setType(1); // 追评
                reviewImageMapper.insert(image);
            }
        }

        // 3. 更新MongoDB
        Query query = new Query(Criteria.where("orderId").is(orderNo).and("userId").is(userId));
        ReviewDetail detail = mongoTemplate.findOne(query, ReviewDetail.class);
        if (detail != null) {
            detail.setAppendContent(appendContent);
            detail.setAppendImages(appendImages);
            reviewDetailRepository.save(detail);
        }

        log.info("追评成功: orderNo={}", orderNo);
    }

    @Override
    public PageResult<ReviewDetail> getReviewsByProduct(Long productId, Integer rating, Boolean hasImage,
                                                         Integer pageNum, Integer pageSize) {
        Criteria criteria = Criteria.where("productId").is(productId);
        if (rating != null) {
            criteria = criteria.and("rating").is(rating);
        }
        if (Boolean.TRUE.equals(hasImage)) {
            criteria = criteria.and("images").ne(null).and("images").ne(List.of());
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
            Integer r = (Integer) map.get("_id");
            int count = ((Number) map.get("count")).intValue();
            totalCount += count;
            totalRating += r * count;

            switch (r) {
                case 5 -> fiveStar = count;
                case 4 -> fourStar = count;
                case 3 -> threeStar = count;
                case 2 -> twoStar = count;
                case 1 -> oneStar = count;
            }
        }

        stats.setTotalCount(totalCount);
        stats.setAvgRating(totalCount > 0 ? Math.round((double) totalRating / totalCount * 10.0) / 10.0 : 0.0);
        stats.setFiveStar(fiveStar);
        stats.setFourStar(fourStar);
        stats.setThreeStar(threeStar);
        stats.setTwoStar(twoStar);
        stats.setOneStar(oneStar);

        // 统计有图评价数
        long withImageCount = mongoTemplate.count(
                new Query(Criteria.where("productId").is(productId)
                        .and("images").ne(null).and("images").ne(List.of())),
                ReviewDetail.class);
        stats.setWithImageCount((int) withImageCount);

        return stats;
    }

    @Override
    public boolean reviewExists(String orderNo) {
        return reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderNo, orderNo)) > 0;
    }
}
