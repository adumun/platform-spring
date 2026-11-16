package io.adumun.onboarding.fullstack;

import io.adumun.platform.spring.security.AdumunAuthorizationRules;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class FullStackServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FullStackServiceApplication.class, args);
    }

    @GetMapping("/public")
    Map<String, String> publicEndpoint() {
        return Map.of("status", "public");
    }

    @GetMapping("/admin")
    Map<String, String> adminEndpoint() {
        return Map.of("status", "admin");
    }

    @Bean
    AdumunAuthorizationRules authorizationRules() {
        return authorize -> authorize.requestMatchers("/admin").hasAuthority("SCOPE_admin");
    }
}
