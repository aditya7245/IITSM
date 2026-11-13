package com.iitsm.dto;

/** One skill of a technician and how good they are at it, from 1 (beginner) to 5 (expert). */
public record TechnicianSkillResponse(Long skillId, String skillName, int proficiency) { }
