package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.CompensationRepository;
import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.CompensationHistory;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.exception.DuplicateCompensationException;
import com.mindex.challenge.exception.InvalidCompensationException;
import com.mindex.challenge.service.CompensationService;
import com.mindex.challenge.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CompensationServiceImpl implements CompensationService {

    private static final Logger LOG = LoggerFactory.getLogger(CompensationServiceImpl.class);

    private final CompensationRepository compensationRepository;
    private final EmployeeService employeeService;

    public CompensationServiceImpl(CompensationRepository compensationRepository, EmployeeService employeeService) {
        this.compensationRepository = compensationRepository;
        this.employeeService = employeeService;
    }

    @Override
    public Compensation create(String employeeId, Compensation compensation) {
        LOG.debug("Creating compensation for employee id [{}]: [{}]", employeeId, compensation);

        // 404 before 400 before 409: the most fundamental problem with the request is reported.
        Employee employee = employeeService.read(employeeId);
        validate(employeeId, compensation);

        if (compensationRepository.existsByEmployeeIdAndEffectiveDate(employeeId, compensation.getEffectiveDate())) {
            throw new DuplicateCompensationException(employeeId, compensation.getEffectiveDate());
        }

        compensation.setCompensationId(null); // always database-assigned
        compensation.setEmployeeId(employee.getEmployeeId());

        try {
            compensationRepository.insert(compensation);
        } catch (DuplicateKeyException e) {
            // Two requests for the same date raced past the check above; the unique index caught it.
            throw new DuplicateCompensationException(employeeId, compensation.getEffectiveDate());
        }

        compensation.setEmployee(employee);
        return compensation;
    }

    /**
     * Returns every compensation record for the employee, most recent {@code effectiveDate}
     * first, with the current employee carried once on the wrapper. An employee with no records
     * gets an empty list; an unknown employee gets a 404.
     */
    @Override
    public CompensationHistory read(String employeeId) {
        LOG.debug("Reading compensation for employee id [{}]", employeeId);

        Employee employee = employeeService.read(employeeId);
        List<Compensation> compensations =
                compensationRepository.findByEmployeeIdOrderByEffectiveDateDesc(employee.getEmployeeId());

        return new CompensationHistory(employee, compensations);
    }

    private static void validate(String employeeId, Compensation compensation) {
        if (compensation.getSalary() == null) {
            throw new InvalidCompensationException("salary is required");
        }
        if (compensation.getSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidCompensationException("salary must not be negative: " + compensation.getSalary());
        }
        if (compensation.getEffectiveDate() == null) {
            throw new InvalidCompensationException("effectiveDate is required");
        }

        // The URL names the employee. A body that names a different one is a mistake, not a hint.
        Employee bodyEmployee = compensation.getEmployee();
        if (bodyEmployee != null && bodyEmployee.getEmployeeId() != null
                && !bodyEmployee.getEmployeeId().equals(employeeId)) {
            throw new InvalidCompensationException("employee.employeeId in the body [" + bodyEmployee.getEmployeeId()
                    + "] does not match the employeeId in the URL [" + employeeId + "]");
        }
    }
}
