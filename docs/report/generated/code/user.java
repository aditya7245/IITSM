// com/iitsm/domain/user/User.java
public abstract class User extends BaseEntity {
    private String email;
    private String passwordHash;
    private String fullName;
    private boolean active = true;
    // ...
    public abstract Role getRole();
    // ...
    public void changePasswordHash(String newHash) { this.passwordHash = newHash; }
    public void deactivate() { this.active = false; }
}
