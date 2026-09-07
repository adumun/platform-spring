package io.adumun.onboarding.rest;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class RestMinimalApplication {
    public static void main(String[] args) {
        SpringApplication.run(RestMinimalApplication.class, args);
    }

    @GetMapping("/hello")
    Map<String, String> hello() {
        return Map.of("message", "hello");
    }
}
