// com/iitsm/domain/incident/state/IncidentStateMachine.java
public static boolean canTransition(IncidentStatus from, IncidentStatus to) {
    return IncidentStates.of(Objects.requireNonNull(from)).canMoveTo(Objects.requireNonNull(to));
}

/** @throws InvalidStateTransitionException if the move is not allowed, for example NEW to CLOSED */
public static void requireTransition(IncidentStatus from, IncidentStatus to) {
    if (!canTransition(from, to)) {
        throw new InvalidStateTransitionException(from.name(), to.name());
    }
}
