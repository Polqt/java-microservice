package com.auction.auctionservice;

import org.springframework.boot.SpringApplication;

public class TestAuctionserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AuctionserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
