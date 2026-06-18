package com.byw.api.review.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ReviewStatsDTO implements Serializable {
    private Long productId;
    private Integer totalCount;
    private Double avgRating;
    private Integer fiveStar;
    private Integer fourStar;
    private Integer threeStar;
    private Integer twoStar;
    private Integer oneStar;
    private Integer withImageCount;
}
