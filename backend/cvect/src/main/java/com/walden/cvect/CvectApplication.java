package com.walden.cvect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.walden.cvect.repository")
public class CvectApplication {
	public static void main(String[] args) {
		SpringApplication.run(CvectApplication.class, args);
	}
}