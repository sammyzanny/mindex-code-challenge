package com.mindex.challenge.data;

import java.util.List;

/**
 * Every compensation record for one employee, most recent {@code effectiveDate} first. The
 * employee is carried once here rather than repeated on each record. Computed on read, never
 * persisted.
 */
public class CompensationHistory {
    private Employee employee;
    private List<Compensation> compensations;

    public CompensationHistory() {
    }

    public CompensationHistory(Employee employee, List<Compensation> compensations) {
        this.employee = employee;
        this.compensations = compensations;
    }

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public List<Compensation> getCompensations() {
        return compensations;
    }

    public void setCompensations(List<Compensation> compensations) {
        this.compensations = compensations;
    }
}
