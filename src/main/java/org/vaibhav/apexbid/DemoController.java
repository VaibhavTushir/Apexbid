package org.vaibhav.apexbid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {
    @Value("${NODE_ID:local-node}")
    private String nodeId;

    @GetMapping("/hello")
    public String hello() {
        return "Hello from instance: " + nodeId;
    }
}
