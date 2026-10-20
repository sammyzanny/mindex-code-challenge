package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.CompensationRepository;
import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.exception.InvalidCompensationException;
import com.mindex.challenge.service.CompensationService;
import com.mindex.challenge.service.EmployeeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CompensationServiceImpl implements CompensationService {

    private static final Logger LOG = LoggerFactory.getLogger(CompensationServiceImpl.class);

    @Autowired
    private CompensationRepository compensationRepository;

    @Autowired
    private EmployeeService employeeService;

    @Override
    public Compensation create(String employeeId, Compensation compensation) {
        LOG.debug("Creating compensation for employee id [{}]: [{}]", employeeId, compensation);

        // 404 before 400: a request for an employee that does not exist is answered first.
        Employee employee = employeeService.read(employeeId);
        validate(compensation);

        compensation.setEmployeeId(employee.getEmployeeId());
        compensationRepository.insert(compensation);

        compensation.setEmployee(employee);
        return compensation;
    }

    /**
     * Returns every compensation record for the employee, most recent {@code effectiveDate}
     * first, with the current employee attached to each. An employee with no records gets an
     * empty list; an unknown employee gets a 404.
     */
    @Override
    public List<Compensation> read(String employeeId) {
        LOG.debug("Reading compensation for employee id [{}]", employeeId);

        Employee employee = employeeService.read(employeeId);

        List<Compensation> compensations =
                compensationRepository.findByEmployeeIdOrderByEffectiveDateDesc(employee.getEmployeeId());
        compensations.forEach(compensation -> compensation.setEmployee(employee));

        return compensations;
    }

    private static void validate(Compensation compensation) {
        if (compensation == null) {
            throw new InvalidCompensationException("A compensation body is required");
        }
        if (compensation.getSalary() == null) {
            throw new InvalidCompensationException("salary is required");
        }
        if (compensation.getSalary().compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidCompensationException("salary must not be negative: " + compensation.getSalary());
        }
        if (compensation.getEffectiveDate() == null) {
            throw new InvalidCompensationException("effectiveDate is required");
        }
    }
}
