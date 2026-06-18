package com.byw.common.core.result;

import com.byw.common.core.exception.ResultCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private long timestamp;

    public R() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        R<T> r = new R<>();
        r.setCode(ResultCode.SUCCESS.getCode());
        r.setMessage(ResultCode.SUCCESS.getMessage());
        return r;
    }

    public static <T> R<T> ok(T data) {
        R<T> r = ok();
        r.setData(data);
        return r;
    }

    public static <T> R<T> ok(String message, T data) {
        R<T> r = ok();
        r.setMessage(message);
        r.setData(data);
        return r;
    }

    public static <T> R<T> fail() {
        R<T> r = new R<>();
        r.setCode(ResultCode.FAIL.getCode());
        r.setMessage(ResultCode.FAIL.getMessage());
        return r;
    }

    public static <T> R<T> fail(String message) {
        R<T> r = fail();
        r.setMessage(message);
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> R<T> fail(ResultCode resultCode) {
        R<T> r = new R<>();
        r.setCode(resultCode.getCode());
        r.setMessage(resultCode.getMessage());
        return r;
    }

    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
