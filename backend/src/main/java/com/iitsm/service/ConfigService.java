package com.iitsm.service;

import com.iitsm.domain.config.SystemConfig;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.repository.SystemConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and updates admin-changeable settings. Every getter takes a default that is returned when the
 * setting is missing, or (for numbers) when the stored text is not a valid number.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final SystemConfigRepository repository;

    public ConfigService(SystemConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public String getString(String key, String defaultValue) {
        return repository.findById(key).map(SystemConfig::getConfigValue).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public int getInt(String key, int defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Setting '{}' is not a whole number ('{}'); using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public double getDouble(String key, double defaultValue) {
        String value = getString(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Setting '{}' is not a number ('{}'); using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /** Changes a setting, creating it if it does not exist yet. */
    @Transactional
    public void set(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new InvalidInputException("Setting key must not be blank");
        }
        if (value == null || value.isBlank()) {
            throw new InvalidInputException("Setting value must not be blank");
        }
        SystemConfig config = repository.findById(key).orElseGet(() -> new SystemConfig(key, value, null));
        config.changeValue(value);
        repository.save(config);
    }
}
