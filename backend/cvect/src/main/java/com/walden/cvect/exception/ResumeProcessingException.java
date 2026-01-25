package com.walden.cvect.exception;

/**
 * 简历处理自定义异常
 * 用于封装简历解析、归一化、分块、事实提取过程中抛出的异常
 */
public class ResumeProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 基础构造函数
     *
     * @param message 错误描述信息
     */
    public ResumeProcessingException(String message) {
        super(message);
    }

    /**
     * 带原因的构造函数（推荐使用）
     * 能够保留原始异常的堆栈信息，方便排查问题
     *
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public ResumeProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
