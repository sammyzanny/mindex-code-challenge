package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.service.EmployeeService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EmployeeServiceImplTest {

    private static final String PAUL_MCCARTNEY_ID = "b7839309-3348-463b-a7e3-5de1c168beb3";

    private String employeeUrl;
    private String employeeIdUrl;

    @Autowired
    private EmployeeService employeeService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Before
    public void setup() {
        employeeUrl = "http://localhost:" + port + "/employee";
        employeeIdUrl = "http://localhost:" + port + "/employee/{id}";
    }

    @Test
    public void testCreateReadUpdate() {
        Employee testEmployee = new Employee();
        testEmployee.setFirstName("John");
        testEmployee.setLastName("Doe");
        testEmployee.setDepartment("Engineering");
        testEmployee.setPosition("Developer");

        // Create checks
        Employee createdEmployee = restTemplate.postForEntity(employeeUrl, testEmployee, Employee.class).getBody();

        assertNotNull(createdEmployee.getEmployeeId());
        assertEmployeeEquivalence(testEmployee, createdEmployee);


        // Read checks
        Employee readEmployee = restTemplate.getForEntity(employeeIdUrl, Employee.class, createdEmployee.getEmployeeId()).getBody();
        assertEquals(createdEmployee.getEmployeeId(), readEmployee.getEmployeeId());
        assertEmployeeEquivalence(createdEmployee, readEmployee);


        // Update checks
        readEmployee.setPosition("Development Manager");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Employee updatedEmployee =
                restTemplate.exchange(employeeIdUrl,
                        HttpMethod.PUT,
                        new HttpEntity<Employee>(readEmployee, headers),
                        Employee.class,
                        readEmployee.getEmployeeId()).getBody();

        assertEmployeeEquivalence(readEmployee, updatedEmployee);

        // Read-after-update checks: the update replaced the stored document rather than adding a second one
        Employee rereadEmployee = restTemplate.getForEntity(employeeIdUrl, Employee.class, createdEmployee.getEmployeeId()).getBody();
        assertEmployeeEquivalence(readEmployee, rereadEmployee);
        assertEquals(1, employeeRepository.findByEmployeeIdIn(List.of(createdEmployee.getEmployeeId())).size());
    }

    @Test
    public void testReadUnknownIdReturnsNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(employeeIdUrl, String.class, "does-not-exist");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testUpdateUnknownIdReturnsNotFound() {
        Employee employee = new Employee();
        employee.setFirstName("Nobody");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = restTemplate.exchange(employeeIdUrl,
                HttpMethod.PUT,
                new HttpEntity<>(employee, headers),
                String.class,
                "does-not-exist");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(0, employeeRepository.findByEmployeeIdIn(List.of("does-not-exist")).size());
    }

    @Test
    public void testDirectReportsAcceptIdStringsAndObjects() {
        // The README schema allows directReports items to be either a string id or an object
        String payload = "{"
                + "\"firstName\":\"Mixed\",\"lastName\":\"Reports\","
                + "\"directReports\":[\"" + PAUL_MCCARTNEY_ID + "\",{\"employeeId\":\"03aa1462-ffa9-4978-901b-7c001562cf6f\"}]"
                + "}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Employee> response =
                restTemplate.postForEntity(employeeUrl, new HttpEntity<>(payload, headers), Employee.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Employee created = response.getBody();
        assertNotNull(created);
        assertEquals(2, created.getDirectReports().size());
        assertEquals(PAUL_MCCARTNEY_ID, created.getDirectReports().get(0).getEmployeeId());
        assertEquals("03aa1462-ffa9-4978-901b-7c001562cf6f", created.getDirectReports().get(1).getEmployeeId());

        // and the stub is stored the same way regardless of how it was supplied
        Employee stored = employeeService.read(created.getEmployeeId());
        assertEquals(PAUL_MCCARTNEY_ID, stored.getDirectReports().get(0).getEmployeeId());
    }

    private static void assertEmployeeEquivalence(Employee expected, Employee actual) {
        assertEquals(expected.getFirstName(), actual.getFirstName());
        assertEquals(expected.getLastName(), actual.getLastName());
        assertEquals(expected.getDepartment(), actual.getDepartment());
        assertEquals(expected.getPosition(), actual.getPosition());
    }
}
