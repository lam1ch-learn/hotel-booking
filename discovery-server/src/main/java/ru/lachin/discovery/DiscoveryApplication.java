package ru.lachin.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"ru.lachin.discovery", "ru.lachin.common"})
@org.springframework.cloud.netflix.eureka.server.EnableEurekaServer
public class DiscoveryApplication {
  public static void main(String[] args) {
    SpringApplication.run(DiscoveryApplication.class, args);
  }
}
