package com.mindex.challenge.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One compensation record for one employee. Records are append-only: a change in pay is a new
 * record with a later {@code effectiveDate}, never an edit of an old one.
 * <p>
 * The persisted link to the employee is the {@code employeeId} alone. The full {@code employee}
 * is attached by the service when a record is returned, so the API shows the employee as it is
 * now while the stored document stays small and never goes stale.
 */
public class Compensation {
    /** Persisted link to the employee. Set from the URL, never from the request body. */
    @JsonIgnore
    private String employeeId;

    /** Resolved on read, not stored. */
    @Transient
    private Employee employee;

    private BigDecimal salary;
    private LocalDate effectiveDate;

    public Compensation() {
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public BigDecimal getSalary() {
        return salary;
    }

    public void setSalary(BigDecimal salary) {
        this.salary = salary;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(LocalDate effectiveDate) {
        this.effectiveDate = effectiveDate;
    }
}
