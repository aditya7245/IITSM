package com.iitsm.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The demo settings, read the way the application reads them: application.yml first, then application-demo.yml on
 * top. No database or application start is needed.
 */
class DemoProfileTest {

    private final Binder binder;

    DemoProfileTest() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"), new ClassPathResource("application-demo.yml"));
        Properties properties = yaml.getObject();
        binder = new Binder(new MapConfigurationPropertySource(properties));
    }

    @Test
    void theDemoSpeedsUpTimeAndChecksEveryFiveSeconds() {
        SlaProperties sla = binder.bind("iitsm.sla", SlaProperties.class).get();

        assertEquals(20.0, sla.timeScale());
        assertEquals(5000, sla.monitorIntervalMs());
        assertEquals(0.8, sla.warningThreshold());          // not changed by the demo profile
    }

    @Test
    void theDemoEscalatesEveryMinuteOfScaledTime() {
        EscalationProperties escalation = binder.bind("iitsm.escalation", EscalationProperties.class).get();

        assertEquals(1, escalation.levelTimeoutMinutes());
    }
}
