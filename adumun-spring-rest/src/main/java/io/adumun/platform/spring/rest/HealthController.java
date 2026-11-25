package io.adumun.platform.spring.rest;

import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class HealthController {
    private final List<ReadinessProbe> probes;

    public HealthController(List<ReadinessProbe> probes) {
        this.probes = List.copyOf(probes);
    }

    @GetMapping("/live")
    Map<String, String> live() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    ResponseEntity<Map<String, String>> ready() {
        boolean ready = probes.stream().allMatch(ReadinessProbe::ready);
        Map<String, String> body = Map.of("status", ready ? "UP" : "DOWN");
        return ready ? ResponseEntity.ok(body) : ResponseEntity.status(503).body(body);
    }
}
