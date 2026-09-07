package io.adumun.platform.spring.observability;

import io.micrometer.common.KeyValues;

/** Public observability contract exposed to consumers without coupling to auto-configuration internals. */
public final class AdumunObservationTags {
    private final AdumunObservabilityProperties properties;

    public AdumunObservationTags(AdumunObservabilityProperties properties) {
        this.properties = properties;
    }

    public KeyValues commonTags() {
        return KeyValues.of(
                "service.name", properties.getServiceName(),
                "deployment.environment", properties.getEnvironment(),
                "service.version", properties.getBuildVersion());
    }
}
