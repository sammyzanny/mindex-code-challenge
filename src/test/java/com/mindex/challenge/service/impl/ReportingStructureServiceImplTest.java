package com.mindex.challenge.service.impl;

import com.mindex.challenge.dao.EmployeeRepository;
import com.mindex.challenge.data.Employee;
import com.mindex.challenge.data.ReportingStructure;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReportingStructureServiceImplTest {

    private static final String JOHN_LENNON_ID = "16a596ae-edd3-4847-99fe-c4518e82c86f";
    private static final String PAUL_MCCARTNEY_ID = "b7839309-3348-463b-a7e3-5de1c168beb3";
    private static final String RINGO_STARR_ID = "03aa1462-ffa9-4978-901b-7c001562cf6f";

    /** Upper bound for tests whose failure mode would otherwise be an endless traversal. */
    private static final long TRAVERSAL_TIMEOUT_SECONDS = 5;

    private String employeeUrl;
    private String employeeIdUrl;
    private String reportingStructureIdUrl;

    @Autowired
    private EmployeeRepository employeeRepository;

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        employeeUrl = "http://localhost:" + port + "/employee";
        employeeIdUrl = "http://localhost:" + port + "/employee/{id}";
        reportingStructureIdUrl = "http://localhost:" + port + "/reportingStructure/{id}";
    }

    @Test
    public void testReadmeExample() {
        // John Lennon -> Paul McCartney, Ringo Starr -> Pete Best, George Harrison
        ReportingStructure reportingStructure = readReportingStructure(JOHN_LENNON_ID);

        assertEquals(4, reportingStructure.getNumberOfReports());
        assertEquals(JOHN_LENNON_ID, reportingStructure.getEmployee().getEmployeeId());
        assertEquals("John", reportingStructure.getEmployee().getFirstName());
        assertEquals("Lennon", reportingStructure.getEmployee().getLastName());
    }

    @Test
    public void testOneLevelManager() {
        // Ringo Starr -> Pete Best, George Harrison
        ReportingStructure reportingStructure = readReportingStructure(RINGO_STARR_ID);

        assertEquals(2, reportingStructure.getNumberOfReports());
        assertEquals(RINGO_STARR_ID, reportingStructure.getEmployee().getEmployeeId());
    }

    @Test
    public void testLeafEmployee() {
        // Paul McCartney has no directReports at all
        ReportingStructure reportingStructure = readReportingStructure(PAUL_MCCARTNEY_ID);

        assertEquals(0, reportingStructure.getNumberOfReports());
        assertEquals(PAUL_MCCARTNEY_ID, reportingStructure.getEmployee().getEmployeeId());
    }

    @Test
    public void testSharedReportCountedOnce() {
        // top -> managerA, managerB ; managerA -> shared ; managerB -> shared
        Employee shared = createEmployee("Shared", "Report");
        Employee managerA = createEmployee("Manager", "A", shared);
        Employee managerB = createEmployee("Manager", "B", shared);
        Employee top = createEmployee("Top", "Level", managerA, managerB);

        assertEquals(1, readReportingStructure(managerA.getEmployeeId()).getNumberOfReports());
        assertEquals(1, readReportingStructure(managerB.getEmployeeId()).getNumberOfReports());
        // managerA, managerB and shared: shared is reachable twice but counted once
        assertEquals(3, readReportingStructure(top.getEmployeeId()).getNumberOfReports());
    }

    @Test
    @Timeout(value = TRAVERSAL_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    public void testCycleTerminates() {
        // cycleA -> cycleB -> cycleA. Inserted through the repository because a mutual reference
        // needs both ids to exist before either document is written.
        Employee cycleA = newEmployeeWithId("Cycle", "A");
        Employee cycleB = newEmployeeWithId("Cycle", "B");

        cycleA.setDirectReports(Collections.singletonList(stub(cycleB)));
        cycleB.setDirectReports(Collections.singletonList(stub(cycleA)));

        employeeRepository.insert(cycleA);
        employeeRepository.insert(cycleB);

        // each sees the other exactly once and never counts itself
        assertEquals(1, readReportingStructure(cycleA.getEmployeeId()).getNumberOfReports());
        assertEquals(1, readReportingStructure(cycleB.getEmployeeId()).getNumberOfReports());
    }

    @Test
    @Timeout(value = TRAVERSAL_TIMEOUT_SECONDS, unit = TimeUnit.SECONDS)
    public void testSelfReferenceIsNotCounted() {
        Employee self = newEmployeeWithId("Self", "Reference");
        self.setDirectReports(Collections.singletonList(stub(self)));
        employeeRepository.insert(self);

        assertEquals(0, readReportingStructure(self.getEmployeeId()).getNumberOfReports());
    }

    @Test
    public void testDanglingReportIsCountedAndBlankIdsAreIgnored() {
        Employee dangling = new Employee();
        dangling.setEmployeeId(UUID.randomUUID().toString()); // never stored

        Employee blank = new Employee();
        blank.setEmployeeId("");

        Employee noId = new Employee();
        noId.setFirstName("no id at all");

        Employee manager = new Employee();
        manager.setFirstName("Dangling");
        manager.setLastName("Manager");
        manager.setDirectReports(Arrays.asList(dangling, blank, null, noId, stub(paulMcCartney())));

        Employee created = restTemplate.postForEntity(employeeUrl, manager, Employee.class).getBody();
        assertNotNull(created);

        // the dangling id and Paul count; the blank, null and id-less entries do not
        assertEquals(2, readReportingStructure(created.getEmployeeId()).getNumberOfReports());
    }

    @Test
    public void testUnknownEmployeeIdReturnsNotFound() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(reportingStructureIdUrl, String.class, "does-not-exist");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    public void testStructureSurvivesUpdateOfAReport() {
        // Regression: an update used to insert a second document with the same employeeId,
        // which made every lookup of that employee, and of every manager above it, fail.
        Employee ringo = restTemplate.getForEntity(employeeIdUrl, Employee.class, RINGO_STARR_ID).getBody();
        assertNotNull(ringo);
        ringo.setPosition("Developer VI");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Employee> updateResponse = restTemplate.exchange(employeeIdUrl, HttpMethod.PUT,
                new HttpEntity<>(ringo, headers), Employee.class, RINGO_STARR_ID);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

        assertEquals(2, readReportingStructure(RINGO_STARR_ID).getNumberOfReports());
        assertEquals(4, readReportingStructure(JOHN_LENNON_ID).getNumberOfReports());
    }

    @Test
    public void testEmployeeShapeMatchesEmployeeEndpoint() {
        Employee fromEmployeeEndpoint =
                restTemplate.getForEntity(employeeIdUrl, Employee.class, JOHN_LENNON_ID).getBody();
        Employee fromReportingStructure = readReportingStructure(JOHN_LENNON_ID).getEmployee();

        assertNotNull(fromEmployeeEndpoint);
        assertEmployeeEquivalence(fromEmployeeEndpoint, fromReportingStructure);
        assertEquals(fromEmployeeEndpoint.getDirectReports().size(), fromReportingStructure.getDirectReports().size());
        for (int i = 0; i < fromEmployeeEndpoint.getDirectReports().size(); i++) {
            assertEquals(fromEmployeeEndpoint.getDirectReports().get(i).getEmployeeId(),
                    fromReportingStructure.getDirectReports().get(i).getEmployeeId());
        }
    }

    @Test
    public void testNothingIsPersisted() {
        readReportingStructure(JOHN_LENNON_ID);

        Employee johnLennon = employeeRepository.findByEmployeeId(JOHN_LENNON_ID);
        assertNotNull(johnLennon);
        assertEquals(2, johnLennon.getDirectReports().size());
        // the stored stubs are still bare ids: the traversal did not write resolved employees back
        assertNull(johnLennon.getDirectReports().get(0).getFirstName());
    }

    private ReportingStructure readReportingStructure(String employeeId) {
        ReportingStructure reportingStructure =
                restTemplate.getForEntity(reportingStructureIdUrl, ReportingStructure.class, employeeId).getBody();

        assertNotNull(reportingStructure);
        assertNotNull(reportingStructure.getEmployee());

        return reportingStructure;
    }

    private Employee createEmployee(String firstName, String lastName, Employee... directReports) {
        Employee employee = new Employee();
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setDepartment("Engineering");
        employee.setPosition("Developer");

        if (directReports.length > 0) {
            employee.setDirectReports(Arrays.stream(directReports)
                    .map(ReportingStructureServiceImplTest::stub)
                    .toList());
        }

        Employee created = restTemplate.postForEntity(employeeUrl, employee, Employee.class).getBody();
        assertNotNull(created);
        assertNotNull(created.getEmployeeId());

        return created;
    }

    private static Employee newEmployeeWithId(String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setEmployeeId(UUID.randomUUID().toString());
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        return employee;
    }

    private static Employee paulMcCartney() {
        return Employee.fromEmployeeId(PAUL_MCCARTNEY_ID);
    }

    /** Mirrors how directReports are stored: an Employee carrying only the employeeId. */
    private static Employee stub(Employee employee) {
        return Employee.fromEmployeeId(employee.getEmployeeId());
    }

    private static void assertEmployeeEquivalence(Employee expected, Employee actual) {
        assertEquals(expected.getEmployeeId(), actual.getEmployeeId());
        assertEquals(expected.getFirstName(), actual.getFirstName());
        assertEquals(expected.getLastName(), actual.getLastName());
        assertEquals(expected.getDepartment(), actual.getDepartment());
        assertEquals(expected.getPosition(), actual.getPosition());
    }
}
