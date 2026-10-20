package com.mindex.challenge.service.impl;

import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.Employee;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CompensationServiceImplTest {

    private static final String JOHN_LENNON_ID = "16a596ae-edd3-4847-99fe-c4518e82c86f";

    private String employeeUrl;
    private String employeeIdUrl;
    private String compensationIdUrl;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Before
    public void setup() {
        employeeUrl = "http://localhost:" + port + "/employee";
        employeeIdUrl = "http://localhost:" + port + "/employee/{id}";
        compensationIdUrl = "http://localhost:" + port + "/compensation/{id}";
    }

    @Test
    public void testCreateReadRoundTrip() {
        Employee employee = createEmployee("Round", "Trip");

        Compensation request = compensation("75000.50", LocalDate.of(2024, 1, 15));

        // Create checks
        ResponseEntity<Compensation> createResponse =
                restTemplate.postForEntity(compensationIdUrl, request, Compensation.class, employee.getEmployeeId());
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());

        Compensation created = createResponse.getBody();
        assertNotNull(created);
        assertCompensationEquivalence(request, created);
        assertEmployeeEquivalence(employee, created.getEmployee());

        // Read checks
        Compensation[] read = readCompensation(employee.getEmployeeId());
        assertEquals(1, read.length);
        assertCompensationEquivalence(request, read[0]);
        assertEmployeeEquivalence(employee, read[0].getEmployee());

        // The attached employee is the same shape the employee endpoint returns
        Employee fromEmployeeEndpoint =
                restTemplate.getForEntity(employeeIdUrl, Employee.class, employee.getEmployeeId()).getBody();
        assertNotNull(fromEmployeeEndpoint);
        assertEmployeeEquivalence(fromEmployeeEndpoint, read[0].getEmployee());
    }

    @Test
    public void testAttachedEmployeeReflectsCurrentEmployeeData() {
        // Bootstrapped employee with direct reports: the attached employee carries them too
        Compensation request = compensation("120000", LocalDate.of(2023, 6, 1));
        restTemplate.postForEntity(compensationIdUrl, request, Compensation.class, JOHN_LENNON_ID);

        Compensation[] read = readCompensation(JOHN_LENNON_ID);
        assertTrue(read.length >= 1);

        Employee attached = read[0].getEmployee();
        assertEquals("John", attached.getFirstName());
        assertEquals("Lennon", attached.getLastName());
        assertEquals(2, attached.getDirectReports().size());
    }

    @Test
    public void testMultipleRecordsReturnedMostRecentFirst() {
        Employee employee = createEmployee("Several", "Raises");

        // Posted out of order on purpose
        Compensation middle = compensation("60000", LocalDate.of(2023, 1, 1));
        Compensation newest = compensation("70000", LocalDate.of(2025, 1, 1));
        Compensation oldest = compensation("50000", LocalDate.of(2021, 1, 1));

        for (Compensation compensation : new Compensation[] {middle, newest, oldest}) {
            ResponseEntity<Compensation> response =
                    restTemplate.postForEntity(compensationIdUrl, compensation, Compensation.class, employee.getEmployeeId());
            assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        Compensation[] read = readCompensation(employee.getEmployeeId());

        assertEquals(3, read.length);
        assertCompensationEquivalence(newest, read[0]);
        assertCompensationEquivalence(middle, read[1]);
        assertCompensationEquivalence(oldest, read[2]);
        for (Compensation compensation : read) {
            assertEmployeeEquivalence(employee, compensation.getEmployee());
        }
    }

    @Test
    public void testEmployeeWithNoCompensationReturnsEmptyList() {
        Employee employee = createEmployee("No", "Compensation");

        ResponseEntity<Compensation[]> response =
                restTemplate.getForEntity(compensationIdUrl, Compensation[].class, employee.getEmployeeId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().length);
    }

    @Test
    public void testUnknownEmployeeReturnsNotFound() {
        Compensation request = compensation("1", LocalDate.of(2024, 1, 1));

        ResponseEntity<String> createResponse =
                restTemplate.postForEntity(compensationIdUrl, request, String.class, "does-not-exist");
        assertEquals(HttpStatus.NOT_FOUND, createResponse.getStatusCode());

        ResponseEntity<String> readResponse =
                restTemplate.getForEntity(compensationIdUrl, String.class, "does-not-exist");
        assertEquals(HttpStatus.NOT_FOUND, readResponse.getStatusCode());
    }

    @Test
    public void testMissingSalaryIsRejected() {
        Employee employee = createEmployee("Missing", "Salary");

        Compensation request = new Compensation();
        request.setEffectiveDate(LocalDate.of(2024, 1, 1));

        assertRejectedAndNothingStored(employee, request);
    }

    @Test
    public void testMissingEffectiveDateIsRejected() {
        Employee employee = createEmployee("Missing", "Date");

        Compensation request = new Compensation();
        request.setSalary(new BigDecimal("1000"));

        assertRejectedAndNothingStored(employee, request);
    }

    @Test
    public void testNegativeSalaryIsRejected() {
        Employee employee = createEmployee("Negative", "Salary");

        assertRejectedAndNothingStored(employee, compensation("-1", LocalDate.of(2024, 1, 1)));
    }

    @Test
    public void testMalformedDateIsRejected() {
        Employee employee = createEmployee("Bad", "Date");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"salary\": 1000, \"effectiveDate\": \"not-a-date\"}";

        ResponseEntity<String> response = restTemplate.postForEntity(compensationIdUrl,
                new HttpEntity<>(payload, headers), String.class, employee.getEmployeeId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, readCompensation(employee.getEmployeeId()).length);
    }

    @Test
    public void testEmployeeIdInBodyIsIgnoredInFavourOfUrl() {
        Employee employee = createEmployee("Url", "Wins");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String payload = "{\"employeeId\": \"" + JOHN_LENNON_ID + "\", \"salary\": 1000, \"effectiveDate\": \"2024-01-01\"}";

        ResponseEntity<Compensation> response = restTemplate.postForEntity(compensationIdUrl,
                new HttpEntity<>(payload, headers), Compensation.class, employee.getEmployeeId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(employee.getEmployeeId(), response.getBody().getEmployee().getEmployeeId());
        assertEquals(1, readCompensation(employee.getEmployeeId()).length);
    }

    private void assertRejectedAndNothingStored(Employee employee, Compensation request) {
        ResponseEntity<String> response =
                restTemplate.postForEntity(compensationIdUrl, request, String.class, employee.getEmployeeId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, readCompensation(employee.getEmployeeId()).length);
    }

    private Compensation[] readCompensation(String employeeId) {
        ResponseEntity<Compensation[]> response =
                restTemplate.getForEntity(compensationIdUrl, Compensation[].class, employeeId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        return response.getBody();
    }

    private Employee createEmployee(String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setDepartment("Engineering");
        employee.setPosition("Developer");

        Employee created = restTemplate.postForEntity(employeeUrl, employee, Employee.class).getBody();
        assertNotNull(created);
        assertNotNull(created.getEmployeeId());

        return created;
    }

    private static Compensation compensation(String salary, LocalDate effectiveDate) {
        Compensation compensation = new Compensation();
        compensation.setSalary(new BigDecimal(salary));
        compensation.setEffectiveDate(effectiveDate);
        return compensation;
    }

    private static void assertCompensationEquivalence(Compensation expected, Compensation actual) {
        assertNotNull(actual.getSalary());
        assertEquals(0, expected.getSalary().compareTo(actual.getSalary()));
        assertEquals(expected.getEffectiveDate(), actual.getEffectiveDate());
        // the persisted link never leaks into the API payload
        assertNull(actual.getEmployeeId());
    }

    private static void assertEmployeeEquivalence(Employee expected, Employee actual) {
        assertNotNull(actual);
        assertEquals(expected.getEmployeeId(), actual.getEmployeeId());
        assertEquals(expected.getFirstName(), actual.getFirstName());
        assertEquals(expected.getLastName(), actual.getLastName());
        assertEquals(expected.getDepartment(), actual.getDepartment());
        assertEquals(expected.getPosition(), actual.getPosition());
    }
}
