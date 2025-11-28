package com.example.configclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @Value("${greeting.message:No greeting configured}")
    private String greetingMessage;

    @Value("${demo.message:No demo configured}")
    private String demoMessage;

    @GetMapping("/greeting")
    public String greeting() {
         return greetingMessage + ": " + demoMessage;
    }
}
