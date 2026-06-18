package com.byw.review.repository;

import com.byw.review.document.ReviewDetail;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReviewDetailRepository extends MongoRepository<ReviewDetail, String> {
}
