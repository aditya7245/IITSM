package com.iitsm.repository;

import com.iitsm.domain.user.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByNameIgnoreCase(String name);

    /** The team this user leads, if any. */
    Optional<Team> findByLeadId(Long leadId);
}
