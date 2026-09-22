package com.sharkycake.exception;

/**
 * 异常处理工具类
 */
public class ThrowUtils {
    /**
     * 条件成立则抛出异常
     * @param condition
     * @param e
     */
    public static void throwIf(boolean condition,RuntimeException e){
        if (condition){
            throw e;
        }
    }
    /**
     * 条件成立则抛出异常
     * @param condition
     * @param
     */
    public static void throwIf(boolean condition,ErrorCode errorCode){
        throwIf(condition,new BusinessException(errorCode));
    }

    /**
     * 条件成立则抛出异常
     * @param condition
     * @param errorCode
     * @param message
     */
    public static void throwIf(boolean condition,ErrorCode errorCode,String message){
        throwIf(condition,new BusinessException(errorCode,message));
    }

}
