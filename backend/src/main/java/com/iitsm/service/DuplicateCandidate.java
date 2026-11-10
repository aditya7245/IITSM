package com.iitsm.service;

/**
 * An existing open incident that looks like a newly reported one.
 *
 * @param id     database id of the existing incident
 * @param number its readable number, such as INC1001
 * @param title  its title, so the reporter can recognise it
 * @param score  how alike the two reports are, from 0.0 to 1.0 (higher means more alike)
 */
public record DuplicateCandidate(Long id, String number, String title, double score) { }
