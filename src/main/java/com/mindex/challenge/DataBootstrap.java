package com.mindex.challenge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

@Component
public class DataBootstrap {
    private static final String DATASTORE_LOCATION = "/static/employee_database.json";

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        Employee[] employees;

        try (InputStream inputStream = this.getClass().getResourceAsStream(DATASTORE_LOCATION)) {
            if (inputStream == null) {
                throw new IllegalStateException("Bootstrap data not found on classpath: " + DATASTORE_LOCATION);
            }
            employees = objectMapper.readValue(inputStream, Employee[].class);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bootstrap data from " + DATASTORE_LOCATION, e);
        }

        for (Employee employee : employees) {
            employeeRepository.insert(employee);
        }
    }
}
