package com.iitsm.service;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Many incidents reported at the same moment must never push a technician past their workload limit.
 * Before the assignment lock, two reports could both see a technician with one free slot and both take it
 * (a burst of 25 reports left one technician at 8 of 5). This test commits real data, so it cleans up after itself.
 */
class ConcurrentAssignmentIntegrationTest extends AbstractIntegrationTest {

    private static final String PREFIX = "CONCURRENT-ASSIGN ";
    private static final int REPORTS = 20;

    @Autowired IncidentService incidentService;
    @Autowired IncidentRepository incidents;
    @Autowired TechnicianRepository technicians;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() throws InterruptedException {
        // Notifications are written in the background after each commit; retry until none is still arriving.
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                jdbc.execute("truncate table audit_logs");   // protected from DELETE; this is a throwaway test database
                jdbc.update("delete from notifications where incident_id in (select id from incidents where title like ?)", PREFIX + "%");
                jdbc.update("delete from incidents where title like ?", PREFIX + "%");
                return;
            } catch (RuntimeException stillArriving) {
                Thread.sleep(250);
            }
        }
    }

    @Test
    void simultaneousReportsNeverExceedATechniciansWorkloadLimit() throws Exception {
        User reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        Long serviceId = services.findByName("Corporate Email").orElseThrow().getId();
        Long categoryId = categories.findByName("Application").orElseThrow().getId();

        ExecutorService pool = Executors.newFixedThreadPool(REPORTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CreateIncidentResult>> results = new ArrayList<>();
        try {
            for (int i = 0; i < REPORTS; i++) {
                CreateIncidentCommand command = new CreateIncidentCommand(PREFIX + i, "Mail client crashes on start " + i,
                        categoryId, serviceId, (short) 1, (short) 1, 1);
                results.add(pool.submit(() -> {
                    start.await();
                    return incidentService.create(command, reporter);
                }));
            }
            start.countDown();
            int assigned = 0;
            for (Future<CreateIncidentResult> result : results) {
                if (result.get(60, TimeUnit.SECONDS).incident().getStatus() == IncidentStatus.ASSIGNED) {
                    assigned++;
                }
            }

            int capacity = 0;
            for (Technician technician : technicians.findByActiveTrueAndAvailableTrue()) {
                long active = incidents.countByAssigneeIdAndStatusIn(technician.getId(), IncidentStatus.workloadStatuses());
                assertTrue(active <= technician.getMaxWorkload(),
                        technician.getFullName() + " has " + active + " of " + technician.getMaxWorkload());
                capacity += technician.getMaxWorkload();
            }
            assertTrue(assigned > 0, "some reports were assigned");
            assertTrue(assigned <= capacity, assigned + " assigned with a total capacity of " + capacity);
            assertEquals(REPORTS, results.size());
        } finally {
            pool.shutdownNow();
        }
    }
}
