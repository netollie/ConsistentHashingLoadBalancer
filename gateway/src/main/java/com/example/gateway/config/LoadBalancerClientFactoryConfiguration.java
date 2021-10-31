package com.example.gateway.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClientSpecification;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class LoadBalancerClientFactoryConfiguration {
    private static final LoadBalancerClientSpecification CONFIGURATION = new LoadBalancerClientSpecification(
        "default.LoadBalancerAutoConfiguration", new Class<?> [] {LoadBalancerConfiguration.class});

    @Bean
    public LoadBalancerClientFactory loadBalancerClientFactory() throws NoSuchFieldException, IllegalAccessException {
        LoadBalancerClientFactory loadBalancerClientFactory = new LoadBalancerClientFactory();
        loadBalancerClientFactory.setConfigurations(Collections.singletonList(CONFIGURATION));
        return loadBalancerClientFactory;
    }
}
