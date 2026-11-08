package com.iitsm.service;

/**
 * Everything an employee fills in when reporting a problem.
 *
 * @param impact        how many people or functions are hurt, 1 (low) to 3 (high)
 * @param urgency       how soon it must be fixed, 1 (low) to 3 (high)
 * @param affectedUsers roughly how many people are affected (at least 1)
 */
public record CreateIncidentCommand(String title, String description, Long categoryId, Long serviceId,
                                    short impact, short urgency, int affectedUsers) { }
