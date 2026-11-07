package com.iitsm.strategy;

/**
 * Works out an incident's priority. Kept behind an interface (Strategy pattern) so the formula
 * can be replaced without touching the code that creates incidents.
 */
public interface PriorityCalculator {

    /**
     * @param impact              how many people or functions are hurt, 1 (low) to 3 (high)
     * @param urgency             how soon it must be fixed, 1 (low) to 3 (high)
     * @param criticality         how vital the affected service is to the business, 1 to 5
     * @param affectedUsers       number of people affected (at least 1)
     * @param bumpUsersThreshold  at or above this many affected users the priority is raised one tier
     * @throws com.iitsm.exception.InvalidInputException if any input is out of range
     */
    PriorityResult calculate(int impact, int urgency, int criticality, int affectedUsers, int bumpUsersThreshold);
}
