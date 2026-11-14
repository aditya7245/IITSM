// com/iitsm/domain/incident/state/NewState.java
final class NewState extends AbstractIncidentState {
    NewState() {
        super(NEW, CLASSIFIED, CANCELLED, DUPLICATE);
    }
}
