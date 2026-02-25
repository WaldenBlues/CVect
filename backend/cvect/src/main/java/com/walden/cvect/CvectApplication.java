package com.walden.cvect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CVect 应用入口
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.walden.cvect.repository")
@EnableScheduling
public class CvectApplication {
	public static void main(String[] args) {
		SpringApplication.run(CvectApplication.class, args);
	}
}
