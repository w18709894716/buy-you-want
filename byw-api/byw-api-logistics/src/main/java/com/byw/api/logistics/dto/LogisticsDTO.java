package com.byw.api.logistics.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LogisticsDTO implements Serializable {
    private Long id;
    private String orderNo;
    private String companyCode;
    private String companyName;
    private String trackingNo;
    private Integer status;
    private List<TraceDTO> traces;

    @Data
    public static class TraceDTO implements Serializable {
        private String description;
        private String location;
        private LocalDateTime traceTime;
    }
}
