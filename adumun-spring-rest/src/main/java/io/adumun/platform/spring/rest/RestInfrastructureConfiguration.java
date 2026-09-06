package io.adumun.platform.spring.rest;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = RestInfrastructureConfiguration.class)
public class RestInfrastructureConfiguration {
    @Bean
    FilterRegistrationBean<RequestContextFilter> adumunRequestContextFilterRegistration() {
        FilterRegistrationBean<RequestContextFilter> registration =
                new FilterRegistrationBean<>(new RequestContextFilter());
        registration.setName("adumunRequestContextFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
