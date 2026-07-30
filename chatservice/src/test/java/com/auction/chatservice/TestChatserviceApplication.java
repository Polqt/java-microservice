package com.auction.chatservice;

import org.springframework.boot.SpringApplication;

public class TestChatserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChatserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
