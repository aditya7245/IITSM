// com/iitsm/strategy/AssignmentStrategy.java
public interface AssignmentStrategy {
    String name();
    // ...
    List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired);
}
