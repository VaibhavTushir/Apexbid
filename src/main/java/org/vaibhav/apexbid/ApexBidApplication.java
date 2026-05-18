package org.vaibhav.apexbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ApexBidApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApexBidApplication.class, args);
    }

}


