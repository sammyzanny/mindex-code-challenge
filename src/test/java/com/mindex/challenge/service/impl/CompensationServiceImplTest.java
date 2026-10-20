package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.CompensationRepository;
import com.mindex.challenge.data.Compensation;
import com.mindex.challenge.data.CompensationHistory;
import com.mindex.challenge.data.Employee;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CompensationServiceImplTest {

    private static final String PAUL_MCCARTNEY_ID = "b7839309-3348-463b-a7e3-5de1c168beb3";

    private String employeeUrl;
    private String employeeIdUrl;
    private String compensationIdUrl;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private CompensationRepository compensationRepository;

    @BeforeEach
    public void setup() {
        employeeUrl = "http://localhost:" + port + "/employee";
        employeeIdUrl = "http://localhost:" + port + "/employee/{id}";
        compensationIdUrl = "http://localhost:" + port + "/compensation/{id}";
    }

    @Test
    public void testCreateReadRoundTrip() {
        Employee employee = createEmployee("Round", "Trip");

        Compensation request = compensation("75000.50", LocalDate.of(2024, 1, 15));

        // Create checks: a single record comes back with the employee attached and an id assigned
        ResponseEntity<Compensation> createResponse =
                restTemplate.postForEntity(compensationIdUrl, request, Compensation.class, employee.getEmployeeId());
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());

        Compensation created = createResponse.getBody();
        assertNotNull(created);
        assertNotNull(created.getCompensationId());
        assertCompensationEquivalence(request, created);
        assertEmployeeEquivalence(employee, created.getEmployee());

        // Read checks: the history carries the employee once and the record without it
        CompensationHistory history = readHistory(employee.getEmployeeId());
        assertEmployeeEquivalence(employee, history.getEmployee());
        assertEquals(1, history.getCompensations().size());
        assertCompensationEquivalence(request, history.getCompensations().get(0));
        assertEquals(created.getCompensationId(), history.getCompensations().get(0).getCompensationId());
        assertNull(history.getCompensations().get(0).getEmployee());

        // The employee on the history is the same shape the employee endpoint returns
        Employee fromEmployeeEndpoint =
                restTemplate.getForEntity(employeeIdUrl, Employee.class, employee.getEmployeeId()).getBody();
        assertNotNull(fromEmployeeEndpoint);
        assertEmployeeEquivalence(fromEmployeeEndpoint, history.getEmployee());
    }

    @Test
    public void testHistoryEmployeeReflectsCurrentEmployeeData() {
        // A fresh manager with a direct report: the attached employee carries the report stub
        Employee manager = new Employee();
        manager.setFirstName("Comp");
        manager.setLastName("Manager");
        manager.setDirectReports(Collections.singletonList(Employee.fromEmployeeId(PAUL_MCCARTNEY_ID)));
        manager = restTemplate.postForEntity(employeeUrl, manager, Employee.class).getBody();
        assertNotNull(manager);

        restTemplate.postForEntity(compensationIdUrl, compensation("120000", LocalDate.of(2023, 6, 1)),
                Compensation.class, manager.getEmployeeId());

        CompensationHistory history = readHistory(manager.getEmployeeId());
        assertEquals(1, history.getCompensations().size());
        assertEquals("Comp", history.getEmployee().getFirstName());
        assertEquals(1, history.getEmployee().getDirectReports().size());
        assertEquals(PAUL_MCCARTNEY_ID, history.getEmployee().getDirectReports().get(0).getEmployeeId());
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

        List<Compensation> compensations = readHistory(employee.getEmployeeId()).getCompensations();

        assertEquals(3, compensations.size());
        assertCompensationEquivalence(newest, compensations.get(0));
        assertCompensationEquivalence(middle, compensations.get(1));
        assertCompensationEquivalence(oldest, compensations.get(2));
    }

    @Test
    public void testDuplicateEffectiveDateIsRejectedWithConflict() {
        Employee employee = createEmployee("Duplicate", "Date");
        LocalDate date = LocalDate.of(2024, 5, 1);

        ResponseEntity<Compensation> first =
                restTemplate.postForEntity(compensationIdUrl, compensation("1000", date), Compensation.class, employee.getEmployeeId());
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second =
                restTemplate.postForEntity(compensationIdUrl, compensation("2000", date), String.class, employee.getEmployeeId());
        assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
        assertNotNull(second.getBody());
        assertTrue(second.getBody().contains("already has a compensation record effective 2024-05-01"), second.getBody());

        // only the first record survived, with its original salary
        List<Compensation> compensations = readHistory(employee.getEmployeeId()).getCompensations();
        assertEquals(1, compensations.size());
        assertEquals(0, new BigDecimal("1000").compareTo(compensations.get(0).getSalary()));
    }

    @Test
    public void testEmployeeWithNoCompensationReturnsEmptyHistory() {
        Employee employee = createEmployee("No", "Compensation");

        CompensationHistory history = readHistory(employee.getEmployeeId());

        assertEmployeeEquivalence(employee, history.getEmployee());
        assertNotNull(history.getCompensations());
        assertEquals(0, history.getCompensations().size());
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
    public void testMissingSalaryIsRejectedWithMessage() {
        Employee employee = createEmployee("Missing", "Salary");

        Compensation request = new Compensation();
        request.setEffectiveDate(LocalDate.of(2024, 1, 1));

        assertRejectedAndNothingStored(employee, request, "salary is required");
    }

    @Test
    public void testMissingEffectiveDateIsRejectedWithMessage() {
        Employee employee = createEmployee("Missing", "Date");

        Compensation request = new Compensation();
        request.setSalary(new BigDecimal("1000"));

        assertRejectedAndNothingStored(employee, request, "effectiveDate is required");
    }

    @Test
    public void testNegativeSalaryIsRejectedWithMessage() {
        Employee employee = createEmployee("Negative", "Salary");

        assertRejectedAndNothingStored(employee, compensation("-1", LocalDate.of(2024, 1, 1)), "must not be negative");
    }

    @Test
    public void testMalformedDateIsRejected() {
        Employee employee = createEmployee("Bad", "Date");

        ResponseEntity<String> response = postJson("{\"salary\": 1000, \"effectiveDate\": \"not-a-date\"}", employee.getEmployeeId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, readHistory(employee.getEmployeeId()).getCompensations().size());
    }

    @Test
    public void testEmployeeIdInBodyIsIgnoredInFavourOfUrl() {
        Employee employee = createEmployee("Url", "Wins");

        ResponseEntity<String> response = postJson(
                "{\"employeeId\": \"" + PAUL_MCCARTNEY_ID + "\", \"salary\": 1000, \"effectiveDate\": \"2024-01-01\"}",
                employee.getEmployeeId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, readHistory(employee.getEmployeeId()).getCompensations().size());
        assertEquals(0, readHistory(PAUL_MCCARTNEY_ID).getCompensations().size());
    }

    @Test
    public void testConflictingEmployeeInBodyIsRejected() {
        Employee employee = createEmployee("Body", "Conflict");

        ResponseEntity<String> response = postJson(
                "{\"employee\": {\"employeeId\": \"" + PAUL_MCCARTNEY_ID + "\"}, \"salary\": 1000, \"effectiveDate\": \"2024-01-01\"}",
                employee.getEmployeeId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("does not match the employeeId in the URL"), response.getBody());
        assertEquals(0, readHistory(employee.getEmployeeId()).getCompensations().size());
    }

    @Test
    public void testUniqueIndexGuardsAgainstRacingDuplicates() {
        // The service checks for a duplicate before inserting, but two concurrent requests can
        // both pass that check. The unique index is the real guard, so prove it exists and bites
        // by bypassing the service and writing straight to the repository.
        Employee employee = createEmployee("Index", "Guard");
        LocalDate date = LocalDate.of(2024, 7, 1);

        Compensation first = compensation("1", date);
        first.setEmployeeId(employee.getEmployeeId());
        Compensation second = compensation("2", date);
        second.setEmployeeId(employee.getEmployeeId());

        compensationRepository.insert(first);
        assertThrows(DuplicateKeyException.class, () -> compensationRepository.insert(second));

        IndexInfo index = mongoTemplate.indexOps("compensation").getIndexInfo().stream()
                .filter(info -> "employee_effective_date".equals(info.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(index, "unique compound index was not created");
        assertTrue(index.isUnique());
        assertEquals(1, readHistory(employee.getEmployeeId()).getCompensations().size());
    }

    @Test
    public void testStoredDocumentShape() {
        // Pins the persistence decisions: date as a zone-free ISO string, salary as an exact
        // string, the link as employeeId only, and no embedded employee.
        Employee employee = createEmployee("Stored", "Shape");
        restTemplate.postForEntity(compensationIdUrl, compensation("75000.123456789", LocalDate.of(2024, 3, 10)),
                Compensation.class, employee.getEmployeeId());

        List<Document> documents = mongoTemplate.find(
                Query.query(Criteria.where("employeeId").is(employee.getEmployeeId())), Document.class, "compensation");

        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertEquals("2024-03-10", document.get("effectiveDate"));
        assertEquals("75000.123456789", document.get("salary"));
        assertEquals(employee.getEmployeeId(), document.get("employeeId"));
        assertNotNull(document.get("_id"));
        assertFalse(document.containsKey("employee"));
    }

    private void assertRejectedAndNothingStored(Employee employee, Compensation request, String expectedMessage) {
        ResponseEntity<String> response =
                restTemplate.postForEntity(compensationIdUrl, request, String.class, employee.getEmployeeId());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains(expectedMessage), response.getBody());
        assertEquals(0, readHistory(employee.getEmployeeId()).getCompensations().size());
    }

    private ResponseEntity<String> postJson(String payload, String employeeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(compensationIdUrl, new HttpEntity<>(payload, headers), String.class, employeeId);
    }

    private CompensationHistory readHistory(String employeeId) {
        ResponseEntity<CompensationHistory> response =
                restTemplate.getForEntity(compensationIdUrl, CompensationHistory.class, employeeId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getEmployee());
        assertNotNull(response.getBody().getCompensations());

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
