package com.example.demoproject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GreetingController {

    @Value("${greeting.message:No greeting configured}")
    private String greetingMessage;

    @Value("${welcome.message:No welcome configured}")
    private String welcomeMessage;

    @GetMapping("/hello")
    public String greeting() {
        return greetingMessage + ": " + welcomeMessage;
    }
}
