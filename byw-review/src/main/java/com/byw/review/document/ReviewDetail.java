package com.byw.review.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document("review_details")
public class ReviewDetail {

    @Id
    private String id;

    private String orderId;

    private Long productId;

    private Long userId;

    private Integer rating;

    private String content;

    private List<String> images;

    private List<String> videos;

    private String appendContent;

    private List<String> appendImages;

    private LocalDateTime createdAt;
}
