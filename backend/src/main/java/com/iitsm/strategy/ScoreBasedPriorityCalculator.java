package com.iitsm.strategy;

import com.iitsm.domain.incident.Priority;
import com.iitsm.exception.InvalidInputException;
import org.springframework.stereotype.Component;

/**
 * Priority = impact x urgency x business criticality, mapped to a tier:
 * 27 or more is P1, 12 to 26 is P2, 5 to 11 is P3, below 5 is P4.
 * If many users are affected the tier is raised one step (P1 stays P1). The returned score is the raw product.
 */
@Component
public class ScoreBasedPriorityCalculator implements PriorityCalculator {

    @Override
    public PriorityResult calculate(int impact, int urgency, int criticality, int affectedUsers, int bumpUsersThreshold) {
        require(impact >= 1 && impact <= 3, "Impact must be between 1 and 3");
        require(urgency >= 1 && urgency <= 3, "Urgency must be between 1 and 3");
        require(criticality >= 1 && criticality <= 5, "Business criticality must be between 1 and 5");
        require(affectedUsers >= 1, "Affected users must be at least 1");
        require(bumpUsersThreshold >= 1, "Bump threshold must be at least 1");

        int score = impact * urgency * criticality;
        Priority priority = tierFor(score);
        if (affectedUsers >= bumpUsersThreshold) {
            priority = priority.bump();
        }
        return new PriorityResult(score, priority);
    }

    private static Priority tierFor(int score) {
        if (score >= 27) return Priority.P1;
        if (score >= 12) return Priority.P2;
        if (score >= 5) return Priority.P3;
        return Priority.P4;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidInputException(message);
        }
    }
}
