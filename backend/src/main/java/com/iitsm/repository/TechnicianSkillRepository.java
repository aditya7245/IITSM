package com.iitsm.repository;

import com.iitsm.domain.catalog.TechnicianSkill;
import com.iitsm.domain.catalog.TechnicianSkillId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TechnicianSkillRepository extends JpaRepository<TechnicianSkill, TechnicianSkillId> {

    @Query("select ts from TechnicianSkill ts where ts.id.technicianId in :technicianIds")
    List<TechnicianSkill> findByTechnicianIdIn(@Param("technicianIds") Collection<Long> technicianIds);
}
