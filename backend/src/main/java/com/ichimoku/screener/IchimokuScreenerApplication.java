package com.ichimoku.screener;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IchimokuScreenerApplication {

	public static void main(String[] args) {
		SpringApplication.run(IchimokuScreenerApplication.class, args);
	}
}
