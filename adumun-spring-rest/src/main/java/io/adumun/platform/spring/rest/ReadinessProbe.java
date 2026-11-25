package io.adumun.platform.spring.rest;

@FunctionalInterface
public interface ReadinessProbe {
    boolean ready();
}
