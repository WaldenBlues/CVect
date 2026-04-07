package com.walden.cvect.logging.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AppLog {

    String action() default "";

    boolean logArgs() default true;

    boolean logResult() default false;

    long slowThresholdMs() default -1L;
}
