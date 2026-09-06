package io.adumun.platform.spring.reference;

import io.adumun.platform.spring.rest.ApiEnvelope;
import io.adumun.platform.spring.rest.RequestContextHolder;
import io.adumun.platform.spring.rest.RestFailureException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DemoController {
    @GetMapping("/demo")
    ApiEnvelope<Map<String, String>> demo() {
        return ApiEnvelope.success(Map.of("message", "ok"), RequestContextHolder.current().orElseThrow());
    }

    @GetMapping("/demo/failure")
    void failure() {
        throw new RestFailureException(503, "DEPENDENCY_UNAVAILABLE", "DEPENDENCY", true, "Dependency unavailable");
    }

    @GetMapping("/demo/internal")
    void internal() {
        throw new IllegalStateException("secret internal detail");
    }
}
