// com/iitsm/domain/user/Technician.java
public class Technician extends User {
    @Override public Role getRole() { return Role.TECHNICIAN; }
    // ...
    public boolean hasCapacity(int activeIncidents) {
        return available && activeIncidents < maxWorkload;
    }
    public void requireCapacity(int activeIncidents) {
        if (activeIncidents >= maxWorkload) {
            throw new WorkloadExceededException(getFullName(), maxWorkload);
        }
    }
}
