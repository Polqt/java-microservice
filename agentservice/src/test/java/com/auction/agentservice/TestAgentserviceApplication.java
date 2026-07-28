package com.auction.agentservice;

import org.springframework.boot.SpringApplication;

public class TestAgentserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgentserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
