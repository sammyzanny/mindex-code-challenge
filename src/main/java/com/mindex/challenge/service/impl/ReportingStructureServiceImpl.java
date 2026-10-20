package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import com.mindex.challenge.service.EmployeeService;
import com.mindex.challenge.service.ReportingStructureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ReportingStructureServiceImpl implements ReportingStructureService {

    private static final Logger LOG = LoggerFactory.getLogger(ReportingStructureServiceImpl.class);

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    public ReportingStructureServiceImpl(EmployeeService employeeService, EmployeeRepository employeeRepository) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public ReportingStructure read(String id) {
        LOG.debug("Computing reporting structure for employee id [{}]", id);

        Employee employee = employeeService.read(id);

        return new ReportingStructure(employee, countReports(employee));
    }

    /**
     * Counts every distinct employee reachable through {@code directReports}, at any depth.
     * <p>
     * Stored {@code directReports} entries only carry an {@code employeeId}, so each level of the
     * tree is resolved against the repository with a single {@code in} query before the next level
     * is examined: the cost is one query per level of depth rather than one per employee.
     * <p>
     * A visited set keeps every employee counted at most once (a report shared by two managers
     * counts once) and makes the traversal terminate even if the data contains a cycle. The root
     * employee is seeded into the visited set so a cycle leading back to it never counts it as its
     * own report. A listed id that matches no stored employee is still counted, because the
     * employee's own {@code directReports} says it is a report, but it is logged as a data problem.
     * Entries with a null or blank id carry no information and are ignored.
     */
    private int countReports(Employee root) {
        Set<String> visited = new HashSet<>();
        visited.add(root.getEmployeeId());

        List<Employee> level = Collections.singletonList(root);
        int numberOfReports = 0;

        while (!level.isEmpty()) {
            Set<String> nextLevelIds = new LinkedHashSet<>();

            for (Employee employee : level) {
                List<Employee> directReports = employee.getDirectReports();

                if (directReports == null) {
                    continue;
                }

                for (Employee directReport : directReports) {
                    if (directReport == null || directReport.getEmployeeId() == null
                            || directReport.getEmployeeId().isBlank()) {
                        continue;
                    }

                    if (visited.add(directReport.getEmployeeId())) {
                        nextLevelIds.add(directReport.getEmployeeId());
                    }
                }
            }

            numberOfReports += nextLevelIds.size();

            if (nextLevelIds.isEmpty()) {
                break;
            }

            level = employeeRepository.findByEmployeeIdIn(nextLevelIds);

            if (level.size() != nextLevelIds.size()) {
                Set<String> missing = new LinkedHashSet<>(nextLevelIds);
                level.forEach(employee -> missing.remove(employee.getEmployeeId()));
                LOG.warn("Employee ids {} are listed as direct reports but do not exist", missing);
            }
        }

        return numberOfReports;
    }
}
