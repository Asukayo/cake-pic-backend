package com.sharkycake.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, final String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public  BusinessException(ErrorCode errorCode,String message) {
        super(message);
        this.code = errorCode.getCode();
    }


}
