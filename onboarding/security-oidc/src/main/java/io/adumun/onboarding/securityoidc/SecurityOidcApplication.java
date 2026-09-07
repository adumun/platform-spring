package io.adumun.onboarding.securityoidc;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class SecurityOidcApplication {
    public static void main(String[] args) {
        SpringApplication.run(SecurityOidcApplication.class, args);
    }

    @GetMapping("/secure")
    Map<String, String> secure() {
        return Map.of("status", "secure");
    }
}
