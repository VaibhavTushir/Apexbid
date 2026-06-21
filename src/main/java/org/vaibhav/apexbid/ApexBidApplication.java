package org.vaibhav.apexbid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.resilience.annotation.EnableResilientMethods;
@EnableScheduling
@EnableResilientMethods
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
public class ApexBidApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApexBidApplication.class, args);
    }

}


