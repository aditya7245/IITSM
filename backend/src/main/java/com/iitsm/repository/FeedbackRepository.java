package com.iitsm.repository;

import com.iitsm.domain.incident.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByIncidentId(Long incidentId);
    boolean existsByIncidentId(Long incidentId);
}
