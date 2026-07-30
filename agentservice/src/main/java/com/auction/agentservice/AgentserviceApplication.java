package com.auction.agentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AgentserviceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentserviceApplication.class, args);
	}

}
