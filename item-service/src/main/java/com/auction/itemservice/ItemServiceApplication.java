package com.auction.itemservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Main application class for Item Service.
 *
 * @EnableSpringDataWebSupport: Configures Spring Data web support with stable pagination
 * serialization. - VIA_DTO mode ensures consistent JSON structure across Spring versions - Prevents
 * the warning: "Serializing PageImpl instances as-is is not supported"
 */
@SpringBootApplication
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ItemServiceApplication {

  /**
   * Application entry point that launches the Item Service Spring Boot application.
   *
   * @param args command-line arguments passed to the application
   */
  public static void main(String[] args) {
    SpringApplication.run(ItemServiceApplication.class, args);
  }

}