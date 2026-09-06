package io.adumun.platform.spring.reference;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SecurityDemoController {
    @GetMapping("/security/public")
    Map<String, String> publicEndpoint() {
        return Map.of("access", "public");
    }

    @GetMapping("/security/authenticated")
    Map<String, String> authenticated(Authentication authentication) {
        return Map.of("subject", authentication.getName());
    }

    @GetMapping("/security/admin")
    Map<String, String> admin(Authentication authentication) {
        return Map.of("subject", authentication.getName(), "access", "admin");
    }
}
