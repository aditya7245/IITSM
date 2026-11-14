package com.iitsm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Settings under {@code iitsm.security}.
 *
 * @param jwtSecret            the key that signs login tokens (environment variable {@code JWT_SECRET}). When it is
 *                             not set, a random key is made at start-up: nobody can forge a token from a secret
 *                             published in the source code, at the cost of everyone logging in again after a
 *                             restart. The prod profile requires it to be set.
 * @param jwtExpirationMinutes how long a token stays valid
 * @param allowedOrigins       the browser origins allowed to call the API (CORS)
 */
@ConfigurationProperties(prefix = "iitsm.security")
public record SecurityProperties(String jwtSecret, long jwtExpirationMinutes, List<String> allowedOrigins) {

    private static final Logger log = LoggerFactory.getLogger(SecurityProperties.class);

    public SecurityProperties {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            byte[] random = new byte[48];
            new SecureRandom().nextBytes(random);
            jwtSecret = Base64.getEncoder().encodeToString(random);
            log.warn("JWT_SECRET is not set: using a random signing key, so logins end when the server restarts");
        }
    }
}
