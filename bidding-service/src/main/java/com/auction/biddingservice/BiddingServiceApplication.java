package com.auction.biddingservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BiddingServiceApplication {

  /**
   * Application entry point that boots the Spring Boot application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(BiddingServiceApplication.class, args);
  }

}