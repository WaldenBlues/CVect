package com.walden.cvect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration; // 导入这个

@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class }) // 排除数据库自动配置
public class CvectApplication {
	public static void main(String[] args) {
		SpringApplication.run(CvectApplication.class, args);
	}
}