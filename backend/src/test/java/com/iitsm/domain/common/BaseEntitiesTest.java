package com.iitsm.domain.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BaseEntitiesTest {

    static class Sample extends BaseEntity {
        void preset(Instant t) { initCreatedAt(t); }
    }

    static class SampleCreatedOnly extends CreatedOnlyEntity {
        void preset(Instant t) { initCreatedAt(t); }
    }

    @Test
    void presetCreatedAtIsKeptOnFirstSave() {
        Sample s = new Sample();
        Instant t = Instant.parse("2026-11-05T10:00:00Z");
        s.preset(t);
        s.onCreate();
        assertEquals(t, s.getCreatedAt());
        assertNotNull(s.getUpdatedAt());
    }

    @Test
    void createdAtDefaultsToNowWhenNotPreset() {
        Sample s = new Sample();
        s.onCreate();
        assertNotNull(s.getCreatedAt());
    }

    @Test
    void createdOnlyEntityKeepsPresetValueAndDefaultsOtherwise() {
        SampleCreatedOnly a = new SampleCreatedOnly();
        Instant t = Instant.parse("2026-11-05T10:00:00Z");
        a.preset(t);
        a.onCreate();
        assertEquals(t, a.getCreatedAt());

        SampleCreatedOnly b = new SampleCreatedOnly();
        b.onCreate();
        assertNotNull(b.getCreatedAt());
    }
}
