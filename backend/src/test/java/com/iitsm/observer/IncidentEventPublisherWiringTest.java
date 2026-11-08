package com.iitsm.observer;

import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The application starts and the publisher is created even though no observer exists yet. */
class IncidentEventPublisherWiringTest extends AbstractIntegrationTest {

    @Autowired IncidentEventPublisher publisher;

    @Test
    void publisherIsCreatedByTheApplication() {
        assertNotNull(publisher);
    }
}
