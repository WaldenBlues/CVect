package com.walden.cvect.exception;

/**
 * 简历解析自定义异常
 * 用于封装 Tika 解析过程中抛出的 IO、SAX 或 TikaException
 */
public class ResumeParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 基础构造函数
     * 
     * @param message 错误描述信息
     */
    public ResumeParseException(String message) {
        super(message);
    }

    /**
     * 带原因的构造函数（推荐使用）
     * 能够保留原始异常的堆栈信息，方便排查是文件损坏还是内存溢出
     * 
     * @param message 错误描述信息
     * @param cause   原始异常
     */
    public ResumeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}