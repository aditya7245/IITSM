package com.iitsm.repository;

import com.iitsm.domain.incident.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    Optional<Feedback> findByIncidentId(Long incidentId);
    boolean existsByIncidentId(Long incidentId);

    /** The mean rating of all feedback, or null when there is none. */
    @Query("select avg(f.rating) from Feedback f")
    Double averageRating();
}
