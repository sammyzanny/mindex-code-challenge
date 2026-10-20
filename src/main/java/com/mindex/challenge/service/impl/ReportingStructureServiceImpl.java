package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.service.EmployeeService;
import com.mindex.challenge.service.ReportingStructureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportingStructureServiceImpl implements ReportingStructureService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportingStructureServiceImpl.class);

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Override
    public ReportingStructure read(String id) {
        LOG.debug("Computing reporting structure for employee id [{}]", id);

        Employee employee = employeeService.read(id);

        return new ReportingStructure(employee, countReports(employee));
    }

    /**
     * Counts every distinct employee reachable through {@code directReports}, at any depth.
     * <p>
     * Stored {@code directReports} entries only carry an {@code employeeId}, so each one is
     * resolved against the repository to discover its own reports. A visited set keeps every
     * employee counted at most once (a report shared by two managers counts once) and makes
     * the traversal terminate even if the data contains a cycle. The root employee is seeded
     * into the visited set so that a cycle leading back to it never counts it as its own report.
     */
    private int countReports(Employee root) {
        Set<String> visited = new HashSet<>();
        visited.add(root.getEmployeeId());

        Deque<Employee> pending = new ArrayDeque<>();
        pending.push(root);

        int numberOfReports = 0;

        while (!pending.isEmpty()) {
            Employee current = pending.pop();
            List<Employee> directReports = current.getDirectReports();

            if (directReports == null) {
                continue;
            }

            for (Employee directReport : directReports) {
                if (directReport == null || directReport.getEmployeeId() == null) {
                    continue;
                }

                String reportId = directReport.getEmployeeId();

                if (!visited.add(reportId)) {
                    continue;
                }

                numberOfReports++;

                Employee resolved = employeeRepository.findByEmployeeId(reportId);

                if (resolved != null) {
                    pending.push(resolved);
                } else {
                    LOG.warn("Employee id [{}] is listed as a direct report of [{}] but does not exist",
                            reportId, current.getEmployeeId());
                }
            }
        }

        return numberOfReports;
    }
}
