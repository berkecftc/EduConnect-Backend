package com.educonnect.llmservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient // Eureka Server'a kendini kaydetmesi ve diğer servisleri bulabilmesi için
@EnableFeignClients    // Feign arayüzlerini tarayıp arka planda Proxy sınıflarını (implementasyonları) üretmesi için
public class LlmServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LlmServiceApplication.class, args);
    }
}