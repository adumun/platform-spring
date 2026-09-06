package io.adumun.platform.spring.rest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RestInfrastructureConfiguration.class)
public class RestInfrastructureConfiguration {
    @Bean
    RequestContextFilter requestContextFilter() {
        return new RequestContextFilter();
    }
}
