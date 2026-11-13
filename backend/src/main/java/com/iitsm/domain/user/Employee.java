package com.iitsm.domain.user;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("EMPLOYEE")
public class Employee extends User {

    @Column(length = 100)
    private String department;

    protected Employee() { }

    public Employee(String email, String passwordHash, String fullName, String department) {
        super(email, passwordHash, fullName);
        this.department = department;
    }

    @Override public Role getRole() { return Role.EMPLOYEE; }
    public String getDepartment() { return department; }

    public void changeDepartment(String department) { this.department = department; }
}
