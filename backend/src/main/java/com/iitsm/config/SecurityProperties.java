package com.iitsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "iitsm.security")
public record SecurityProperties(String jwtSecret, long jwtExpirationMinutes, List<String> allowedOrigins) { }
