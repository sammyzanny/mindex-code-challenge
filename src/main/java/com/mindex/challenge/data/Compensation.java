package com.mindex.challenge.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One compensation record for one employee. Records are append-only: a change in pay is a new
 * record with a later {@code effectiveDate}, never an edit of an old one. An employee can have
 * at most one record per {@code effectiveDate}, enforced by a unique index.
 * <p>
 * The persisted link to the employee is the {@code employeeId} alone. The full {@code employee}
 * is attached by the service when a single record is returned, so the API shows the employee as
 * it is now while the stored document stays small and never goes stale. In a
 * {@link CompensationHistory} the employee is carried once by the wrapper and omitted here.
 */
@Document(collection = "compensation")
@CompoundIndex(name = "employee_effective_date", def = "{'employeeId': 1, 'effectiveDate': 1}", unique = true)
public class Compensation {
    /** Assigned by the database on insert. Lets a record be addressed later for corrections. */
    @Id
    private String compensationId;

    /** Persisted link to the employee. Set from the URL, never from the request body. */
    @JsonIgnore
    private String employeeId;

    /** Resolved on read, not stored. Omitted from JSON when not attached. */
    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Employee employee;

    private BigDecimal salary;
    private LocalDate effectiveDate;

    public Compensation() {
    }

    public String getCompensationId() {
        return compensationId;
    }

    public void setCompensationId(String compensationId) {
        this.compensationId = compensationId;
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

    /** Salary is deliberately left out: this string ends up in debug logs. */
    @Override
    public String toString() {
        return "Compensation{compensationId=" + compensationId
                + ", employeeId=" + employeeId
                + ", effectiveDate=" + effectiveDate
                + ", salary=<redacted>}";
    }
}
