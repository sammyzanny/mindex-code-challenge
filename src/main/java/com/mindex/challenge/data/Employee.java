package com.mindex.challenge.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.springframework.data.annotation.Id;

import java.util.List;

public class Employee {
    /**
     * Mapped to the Mongo {@code _id} so that {@code save} replaces the existing document
     * instead of inserting a second one with the same employeeId.
     */
    @Id
    private String employeeId;
    private String firstName;
    private String lastName;
    private String position;
    private String department;
    private List<Employee> directReports;

    public Employee() {
    }

    /**
     * Lets a {@code directReports} entry be given as a bare id string, as the README schema
     * allows ({@code anyOf: string | object}). Object entries still use the default constructor.
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Employee fromEmployeeId(String employeeId) {
        Employee employee = new Employee();
        employee.setEmployeeId(employeeId);
        return employee;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public List<Employee> getDirectReports() {
        return directReports;
    }

    public void setDirectReports(List<Employee> directReports) {
        this.directReports = directReports;
    }

    @Override
    public String toString() {
        return "Employee{employeeId=" + employeeId
                + ", firstName=" + firstName
                + ", lastName=" + lastName
                + ", position=" + position
                + ", department=" + department
                + ", directReports=" + (directReports == null ? 0 : directReports.size()) + "}";
    }
}
