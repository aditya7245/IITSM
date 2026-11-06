package com.iitsm.domain.config;

import jakarta.persistence.*;

/** One runtime setting an admin can change, stored as text (for example assignment.strategy = SKILL_BASED). */
@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "config_key", length = 80)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private String configValue;

    @Column(length = 255)
    private String description;

    protected SystemConfig() { }

    public SystemConfig(String configKey, String configValue, String description) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
    }

    public String getConfigKey() { return configKey; }
    public String getConfigValue() { return configValue; }
    public String getDescription() { return description; }

    public void changeValue(String newValue) {
        this.configValue = newValue;
    }
}
