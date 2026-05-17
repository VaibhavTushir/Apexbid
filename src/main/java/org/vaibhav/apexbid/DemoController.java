package org.vaibhav.apexbid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    @Value("${INSTANCE_ID:local}")
    private String instanceId;

    @GetMapping("/hello")
    public String hello() {
        return "Hello from instance: " + instanceId;
    }
}
