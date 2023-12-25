package com.meng.tbjavagateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
//@ComponentScan(basePackages = "com.meng.tbjavagateway.*")
public class TbJavaGatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(TbJavaGatewayApplication.class, args);
	}

}
