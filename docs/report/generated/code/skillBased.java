// com/iitsm/strategy/SkillBasedStrategy.java
public class SkillBasedStrategy implements AssignmentStrategy {
    private static final Comparator<TechnicianCandidate> ORDER = Comparator
            .comparingInt(TechnicianCandidate::proficiency).reversed()
            .thenComparingInt(TechnicianCandidate::activeIncidents)
            .thenComparing(Comparator.comparingInt((TechnicianCandidate c) -> c.technician().getExperienceYears()).reversed())
            .thenComparing(c -> c.technician().getId(), Comparator.nullsLast(Comparator.naturalOrder()));
}
