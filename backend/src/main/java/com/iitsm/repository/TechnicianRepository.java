package com.iitsm.repository;

import com.iitsm.domain.user.Technician;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TechnicianRepository extends JpaRepository<Technician, Long> {

    /** Technicians who can take work: the account is active and they marked themselves available. Includes team leads. */
    List<Technician> findByActiveTrueAndAvailableTrue();

    /** Every technician with an active account (available or not), team leads included, by name. */
    List<Technician> findByActiveTrueOrderByFullNameAsc();
}
