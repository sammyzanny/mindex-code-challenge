# Design Notes

This document records every change made to the challenge project, in the order it was made, with the reasoning behind each design decision and the alternatives that were considered and rejected. It is meant to be read alongside the README, which describes the API as it now stands.

The work was done in four passes: Task 1, a round of improvements to the existing code, Task 2, and a second round of improvements driven by a critical review of Task 2. Each pass ended with the full test suite green.

---

## 1. Starting point

The project as received is a Spring Boot 3.2.3 service on Java 17 with an in-memory Mongo server (`mongo-java-server`) bootstrapped from a JSON snapshot of five employees. It follows a strict `data` / `dao` / `service` / `controller` layering, one interface plus one implementation per service, SLF4J debug logging at the top of every method, and REST-driven integration tests on a random port.

Several traps were identified before any code was written and shaped the decisions that follow:

- `directReports` is stored as a list of **stub** `Employee` objects carrying only an `employeeId`. Counting reports therefore requires lookups, not a walk of the nested objects.
- `Employee` had no `@Id`, so Mongo generated an `_id` and `employeeId` was an ordinary field. Nothing prevented two documents sharing an `employeeId`.
- `update` called `save` on an object with no `_id`, which **inserted a second document** instead of replacing the first. The existing test did not notice because it never read after updating.
- Unknown ids threw a bare `RuntimeException` and surfaced as HTTP 500.
- The README schema says `directReports` items may be strings or objects, but `List<Employee>` rejected strings with HTTP 400.

---

## 2. Task 1: ReportingStructure

### What was added

| File | Purpose |
|---|---|
| `data/ReportingStructure.java` | `employee` and `numberOfReports` |
| `service/ReportingStructureService.java` | `read(employeeId)` |
| `service/impl/ReportingStructureServiceImpl.java` | Resolves the root, then counts reachable reports |
| `controller/ReportingStructureController.java` | `GET /reportingStructure/{id}` |
| `service/impl/ReportingStructureServiceImplTest.java` | Eleven REST-driven tests |

### Design decisions

**Separate service and controller rather than extending EmployeeService.** Each existing type has its own service interface, implementation and controller. Adding a method to `EmployeeService` would have been fewer files but would mix a derived, read-only view into the entity's CRUD contract and break the one-type-per-controller convention.

**Root lookup through EmployeeService, child lookups through EmployeeRepository.** The root goes through the service so an unknown id fails exactly as the employee endpoint fails, and so the returned `employee` is byte-for-byte the same object `GET /employee/{id}` returns, which the README requires. Children use the repository directly because a dangling reference should be tolerated and logged, not thrown.

**Count distinct referenced ids, not resolved documents.** The README says the count is "determined by the number of directReports", so a listed id counts even if its document is missing, and a warning is logged. Counting only resolvable ids would silently hide broken data and make the count disagree with the employee's own list. Entries with a null or blank id carry no information and are skipped.

**Breadth-first traversal with a visited set seeded with the root.** A `HashSet` of ids gives distinctness (a report shared by two managers counts once) and termination (a cycle is cut at the first repeated id) in one mechanism. Seeding it with the root id means a cycle back to the root never counts the root as its own report. Recursion was rejected because a long chain or a cycle would overflow the stack; a depth limit was rejected because it would silently truncate deep but valid trees.

**One query per level, not one per employee.** The first implementation issued one `findByEmployeeId` per reachable employee, which is an N+1 pattern that would mean ten thousand round trips for a large organisation. The traversal was rewritten to collect each level's ids and resolve them with a single `findByEmployeeIdIn` query, so cost is proportional to tree depth.

**Nothing persisted.** Resolved children live only in the traversal and are never written back. A test asserts the stored stubs still carry only ids after a read.

### Tests

README example (4), a one-level manager (2), a leaf (0), a shared report counted once, a two-node cycle, a self-reference, dangling and blank ids, an unknown id, that the `employee` field matches the employee endpoint, that nothing is persisted, and that the structure survives an update to a report. The cycle and self-reference tests carry a five-second timeout so a regression fails rather than hangs.

---

## 3. First improvement pass (existing code)

The README invites improvements to the existing codebase. A review of Task 1 as a sceptical reader surfaced these, and each was fixed and pinned by a test.

| Problem | Fix |
|---|---|
| `PUT` inserted a duplicate document; every later read of that employee, and of every manager above it, then failed with 500 (`IncorrectResultSizeDataAccessException`). Confirmed live before fixing. | `@Id` on `employeeId` so `save` replaces the document. Update now checks the id exists first, so `PUT` to an unknown id is 404, not an implicit create. |
| Unknown ids returned 500. | `EmployeeNotFoundException` with `@ResponseStatus(NOT_FOUND)`. |
| README schema allowed string items in `directReports`; the model rejected them. | A delegating `@JsonCreator` on `Employee` accepts a bare id string. Object items are unchanged. |
| Copy-paste log messages said "create" for read and update. | Corrected. |
| Bootstrap never closed its stream and gave an opaque error if the seed file was missing. | Try-with-resources and a clear message. |
| The embedded Mongo server was never shut down. | It is now a bean with a `shutdown` destroy method; the test log shows it closing with the context. |
| `build/` and `.gradle/` had been committed. | `.gitignore` added and the generated files unstaged. |

The reporting-structure endpoint was documented in the README at this point.

---

## 4. Task 2: Compensation

### Association options considered

1. **Separate collection, persist only `employeeId`, attach the full `Employee` on read.** Chosen. The stored document is small and can never go stale because the employee is resolved at read time. The one extra lookup doubles as the existence check that produces a clean 404. History is natural because each record is its own document.
2. **Embed a full `Employee` snapshot in each compensation document.** Rejected. Every record would freeze the employee as they were, so a title change never shows in old records, and employee data is duplicated once per record. The README calls the employee an association, not a snapshot.
3. **Embed a compensation list inside the `Employee` document.** Rejected. It changes the shape `GET /employee/{id}` returns, which Task 1 had to preserve; the existing `PUT` replaces the whole document, so a client that does not round-trip the list would wipe pay history; and it leaks salary on every employee read.
4. **Spring Data `@DocumentReference`.** Rejected as option 1 with lazy-loading magic, when `EmployeeService.read` already does the job with the 404 built in.

Two refinements to option 1: the path id is authoritative and the raw `employeeId` never appears in JSON (`@JsonIgnore`), and the attached `employee` is `@Transient` so it is never stored.

### What `GET` returns when there are several records

All of them, as a `CompensationHistory` holding the employee once and the records sorted by `effectiveDate` descending. Create is append-only with no update endpoint, so multiple records are the normal case. Returning only a "current" record would need a clock and a policy for future-dated raises, and would hide history from any consumer that needs it. A sorted list serves both: payroll takes the first record not in the future, HR reads the whole thing. An employee with no records gets an empty list and 200; an unknown employee gets 404.

The first implementation returned a bare `List<Compensation>` with the employee attached to each element. The review pointed out that this repeated the full employee once per record and produced a top-level JSON array with no room for metadata, so the wrapper type was introduced.

### Validation

Done the way the existing code does it: explicit checks in the service throwing `@ResponseStatus` exceptions, with no Bean Validation dependency added. Missing or negative `salary` and missing `effectiveDate` are 400. An unparsable date is rejected by Jackson with 400 before the service runs. The 404 check runs first so a request for a non-existent employee is never answered with a field complaint.

### Endpoints

| Method | URL | Response |
|---|---|---|
| `POST` | `/compensation/{id}` | `Compensation` with the full `Employee` attached and a database-assigned `compensationId` |
| `GET` | `/compensation/{id}` | `CompensationHistory` |

Routing is flat like the other endpoints rather than nested under `/employee/{id}`, to match the existing convention.

### Storage format

- `salary` is a `BigDecimal`, stored by Spring Data as a string. Precision is exact, which is the right trade for money. Mongo cannot range-query a string salary; a `Decimal128` converter is a one-line change if reporting ever needs it.
- `effectiveDate` is a `LocalDate`, stored as an ISO-8601 string (see section 5 for why).

---

## 5. Second improvement pass (Task 2 review)

Task 2 was reviewed with the same sceptical lens and probed empirically against the running application. The findings and fixes, in order of significance:

**Dates were stored as a zoned instant.** Spring Data's default converts `LocalDate` to a `Date` at local midnight using the JVM's default zone in both directions. The stored document literally contained `Sun Mar 10 00:00:00 CST 2024`. Any reader whose zone is more than six hours west of the writer would read the previous day, invisibly in local tests because writer and reader share one JVM. Fixed with two custom converters in `MongoConfig` that store the date as `2024-03-10`. The string still sorts chronologically. A test reads the raw document and pins the format.

**Duplicate and tied effective dates were allowed and unordered.** Two records with the same date for the same employee are ambiguous by definition. A unique compound index on `(employeeId, effectiveDate)` now enforces one record per date, with a pre-check for a clean 409 message and a `DuplicateKeyException` catch for the race the pre-check cannot close. Writing the test for this exposed a further gap: Spring Boot's `spring.data.mongodb.auto-index-creation` property is ignored once a project extends `AbstractMongoClientConfiguration`, so the index was silently never created. The flag is now set by overriding `autoIndexCreation()` in the config class, and the test proves the index exists and that a direct duplicate insert throws.

**Validation messages never reached the client.** Spring Boot 3 defaults `server.error.include-message` to `never`, so a 400 said only "Bad Request". The property is now `always`, and tests assert on the message text for 400, 404 and 409.

**The employee was repeated once per record.** Replaced by the `CompensationHistory` wrapper described in section 4.

**No id on compensation records.** Each record now has a database-assigned `compensationId` so it can be addressed later for corrections. The service always nulls any id supplied in the body before insert.

**A body naming a different employee than the URL was silently overridden.** It is now rejected with 400 and a message. A body naming the same employee, or none, is accepted.

### Cross-cutting changes made in the same pass

- **Constructor injection** replaces `@Autowired` fields in every service, controller and the bootstrap. Dependencies are explicit and the classes can be instantiated without Spring. No behaviour change.
- **JUnit 5** replaces the explicit JUnit 4 dependency. The Boot 3 test starter already ships Jupiter, and the migration removes a legacy runner and enables `@Timeout` and `assertThrows`. No behaviour change.
- **`toString`** on `Employee` and `Compensation`, with salary redacted, because these objects appear in debug logs.
- **GitHub Actions workflow** runs the suite on JDK 17 for every push and pull request and uploads the HTML report on failure.
- **`.gitattributes`** with `text=auto`. The original files were committed with CRLF and later additions with LF; this normalises future commits. Existing files should be renormalised in a dedicated commit (`git add --renormalize .`) so the line-ending churn is not mixed into a code change.
- README documents all endpoints, the 400/404/409 rules and the three JSON schemas.

---

## 6. Test inventory

| Class | Tests | Covers |
|---|---|---|
| `ChallengeApplicationTests` | 1 | Context loads |
| `DataBootstrapTest` | 1 | Seed data present |
| `EmployeeServiceImplTest` | 4 | Create/read/update with read-after-update, 404 on read and update with message, string and object `directReports` |
| `ReportingStructureServiceImplTest` | 11 | See section 2 |
| `CompensationServiceImplTest` | 14 | Round trip, history reflects current employee, ordering of three out-of-order records, 409 on duplicate date, empty history, 404 on both verbs, missing salary / missing date / negative salary with messages, malformed date, body employeeId ignored, conflicting body employee rejected, unique index enforced on a direct insert, raw stored document shape |

Total: 31 tests, all REST-driven integration tests except the two that read the raw Mongo document and the one that inserts through the repository to bypass the service check.

All five classes share one cached Spring context and therefore one in-memory database. Tests create their own employees and never assert on totals, so order does not matter today, but a future test that counts rows would need `@DirtiesContext` or its own fixtures.

---

## 7. Things deliberately left alone

- **`Decimal128` for salary.** Nothing sorts or range-queries salary, and string storage is exact. Noted above as a one-line change.
- **Pagination of compensation history.** Histories are short in practice and the README asks for two endpoints.
- **Upper bounds on salary, scale or future dates.** Scheduled raises are a legitimate reason to allow future dates; the other bounds are business policy the challenge does not specify.
- **A separate DTO layer.** The codebase uses its entities as its API types, and introducing DTOs for two new types would have been inconsistent with the three existing ones.
- **`ReportingStructure` immutability.** It has setters only so Jackson can deserialise it in tests, matching the POJO style of `Employee`.

---

## 8. Running the project

```
gradlew test       # full suite, JDK 17
gradlew bootRun    # http://localhost:8080
```

Gradle 8.6 via the wrapper, Spring Boot 3.2.3, Java 17. Gradle 8.6 does not run on JDK 22 or later, so `JAVA_HOME` must point at a 17 installation even if a newer `java` is on the path.
