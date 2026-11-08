package com.iitsm.strategy;

import com.iitsm.domain.user.Technician;

/**
 * A technician who could take an incident, with the numbers the strategies need.
 *
 * @param activeIncidents how many incidents they are currently working on
 * @param proficiency     their level (1 to 5) in the skill the incident's category requires; 0 if they lack it
 *                        or the category requires no skill
 */
public record TechnicianCandidate(Technician technician, int activeIncidents, int proficiency) { }
