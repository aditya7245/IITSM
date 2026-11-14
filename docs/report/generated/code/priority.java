// com/iitsm/strategy/ScoreBasedPriorityCalculator.java
public class ScoreBasedPriorityCalculator implements PriorityCalculator {
    public PriorityResult calculate(int impact, int urgency, int criticality, int affectedUsers, int bumpUsersThreshold) {
    // ...
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
}
