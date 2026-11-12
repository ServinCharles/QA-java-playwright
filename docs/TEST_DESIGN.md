# Test Design Explanation

This document explains what was automated, why those scenarios were chosen, what would be automated next, and which checks belong at the API layer instead of the UI.

## 1. Approach

- **One framework, two layers.** Playwright drives both the browser (OrangeHRM) and the HTTP calls (Petstore), so there is one toolchain, one runner, and one report.
- **Page Object Model for the UI.** Tests call page objects. Page objects call shared actions in `BasePage`. Selectors live only in the locator classes.
- **A thin client for the API.** `PetStoreClient` knows the endpoint and payload shape. It makes no assertions, so the tests stay readable and the client is reusable.
- **Data outside the code.** Scenarios are JSON files under `src/test/resources/testdata`. A new login case is a new JSON entry.
- **Independent tests.** Each UI test gets a fresh browser context. Each API test creates the data it needs with a random id. No test relies on another test or on data already in the system.

## 2. Implemented scenarios

### 2.1 API: Swagger Petstore, `/pet`

| # | Scenario | Type | Why |
| --- | --- | --- | --- |
| 1 | `POST /pet`, then `GET /pet/{id}` returns the same id, name and status, and both responses match the Pet contract | Positive | Proves the core behaviour: what is written can be read back. It is the path every other pet feature depends on. |
| 2 | `GET /pet/{id}` for an id that does not exist returns 404 with type `error` and message `Pet not found` | Negative | Consumers need a clear "not found". A 200 with an empty body or a 500 would break clients. |
| 3 | `GET /pet/abc` (non-numeric id) returns a 4xx with a well-formed error body | Negative | Invalid input must be rejected as a client error, not surface as a server error. |

Each API test asserts three layers:

1. **Status code.** The outcome the client branches on.
2. **Contract.** The body is validated against a JSON Schema (draft-07) in `src/test/resources/schemas`, using the networknt validator. `pet.schema.json` checks required fields, types, and the `status` enum. `api-error.schema.json` checks that every error has an integer `code` and a non-empty `type` and `message`. The `Content-Type` header must be JSON.
3. **Values.** The fields or error message a consumer relies on, grouped with `assertAll` so one run reports every mismatch.

Notes on the assertions:

- The schemas allow extra fields. A new optional field is not a breaking change, so it should not fail the suite. A removed field, a changed type, or a new enum value does fail it.
- Scenario 2 asserts the exact message `Pet not found`, because that is a deliberate, client-facing message.
- Scenario 3 asserts the 4xx range and the error contract, not the message text. The server returns `java.lang.NumberFormatException: For input string: "abc"`, which is an internal exception leaking to the client. I would raise that as a defect (it should be a 400 with a clean message), and pinning the test to that text would only cement the bug.
- Pet ids are random per run and the pet is deleted afterwards, because the public Petstore is shared with other users. The unknown-id test deletes its id first for the same reason.
- The public server runs on several instances, so a read straight after a write can briefly return 404. The round-trip test polls for up to about 2.5 seconds before failing.

### 2.2 UI: OrangeHRM, login

| # | Scenario | Type | Why |
| --- | --- | --- | --- |
| 1 | Valid credentials land on the dashboard | Positive | Login gates every other feature, so it is the highest-value UI journey. |
| 2 | Correct user with a wrong password shows "Invalid credentials" and stays on the login page | Negative | Confirms access is refused and the user gets feedback. |
| 3 | Unknown user shows "Invalid credentials" | Negative | Same message as scenario 2, which also confirms the app does not reveal whether a username exists. |
| 4 | Empty username and password show "Required" under both fields | Negative | Client-side validation is only visible in the browser, so it belongs in the UI layer. |
| 5 | Username with an empty password shows "Required" under the password field only | Negative | Confirms validation is per field, not all-or-nothing. |

All five run through one data-driven test, so the flow is written once and the cases live in `login.json`.

The error assertions separate the two kinds of failure:

- **Server rejection** (scenarios 2 and 3): the banner is visible and has the exact text.
- **Client validation** (scenarios 4 and 5): the list of field messages matches exactly, in order, which also checks the count. The banner must not appear, which shows the form was stopped before reaching the server.
- Every failed login must also leave the user on the login page.

The assertions are Playwright's web-first assertions, which retry until the condition holds or times out, so there are no fixed waits.

## 3. Additional scenarios, in priority order

Priority is based on business risk first, then how likely the area is to break.

### 3.1 Petstore API

| Priority | Scenario | Type | Approach and rationale |
| --- | --- | --- | --- |
| 1 | `PUT /pet` updates name and status, then `GET` shows the change | Positive | Completes the write path. Updates are a common source of partial-save bugs. |
| 2 | `DELETE /pet/{id}`, then `GET` returns 404 | Positive | Completes the lifecycle and also gives the suite a cleanup step. |
| 3 | `POST /pet` with a missing required field (`name` or `photoUrls`) is rejected | Negative | Schema validation protects data quality. Assert a 4xx. |
| 4 | `POST /pet` with the wrong type (string for `id`) or malformed JSON is rejected | Negative | Malformed input must not cause a 500. |
| 5 | `GET /pet/findByStatus` returns only pets with the requested status | Positive | Filtering logic is easy to get wrong. Assert every item in the list. |
| 6 | `findByStatus` with an invalid status value | Negative | Either a 400 or an empty list. The test pins whichever the contract states. |
| 7 | Contract checks on the remaining endpoints (`findByStatus` list, `/store/order`, `/user`) | Positive | The implemented tests already validate `/pet` against a schema. Extending that catches contract drift everywhere else. |
| 8 | `DELETE` or `PUT` on an id that does not exist returns 404 | Negative | Error handling on the write path. |
| 9 | Boundary ids: 0, negative, larger than a 64-bit integer | Negative | Classic boundary values for a numeric path parameter. |
| 10 | Unsupported method or content type returns 405 or 415 | Negative | Lower risk, cheap to add once the client exists. |

### 3.2 OrangeHRM UI

| Priority | Scenario | Type | Approach and rationale |
| --- | --- | --- | --- |
| 1 | Logout returns to the login page, and the dashboard URL then redirects to login | Positive | Session handling is a security concern. |
| 2 | Admin adds a user, then finds that user in the list | Positive | Core admin journey. Seed prerequisites through the API where one exists, and keep only the journey itself in the UI. |
| 3 | Add user with a duplicate username or mismatched passwords shows a validation error | Negative | Common data-entry mistakes. |
| 4 | User search and filters return the expected rows, and reset clears them | Positive | Table and filter behaviour is heavily used. |
| 5 | Direct navigation to a protected page while logged out redirects to login | Negative | Access control. |
| 6 | A non-admin user cannot see the Admin menu | Negative | Role-based access. |
| 7 | Jobs list shows the expected titles, and add, edit and delete work | Positive | Reference data maintenance. Lower risk than users. |
| 8 | Disabled user cannot log in | Negative | Depends on test data setup, so it comes later. |

## 4. When to test at the API layer instead of the UI

The UI is the slowest and least stable place to test, so it should only cover what needs a browser.

**Keep in the UI:**

- The critical user journeys, end to end: login, and one happy path per major feature.
- Anything only the browser shows: rendering, client-side validation, navigation, redirects.

**Move to the API (or lower):**

- **Business rules and validation.** For example, "duplicate username is rejected" needs one UI test to prove the message appears. The other combinations (length limits, allowed characters, role rules) run faster and more reliably against the endpoint.
- **Data variations.** Twenty input combinations through a form take minutes and can flake. The same twenty as API calls take seconds.
- **Search, filter and sort logic.** The API returns the data, so assert the result set there. One UI test confirms the table is wired to it.
- **Test setup and cleanup.** Creating a user through the UI before testing "edit user" is slow and couples the tests. Seed through the API, then start the UI test at the point of interest.
- **Authorisation rules.** Checking that a role cannot call an endpoint is stronger than checking a menu is hidden, because a hidden menu does not prove the server refuses the request.

**Lower still:** pure logic such as calculations, date rules and field formatting belongs in unit tests, which give feedback in milliseconds and pinpoint the failing line.

The result is the test pyramid: many unit tests, fewer API tests, and a small set of UI tests for the journeys that matter most.

## 5. Trade-offs and challenges

- **Shared public environments.** Both systems are public demos that other people use and that can be reset. Tests therefore create their own data, use random ids, and avoid asserting on exact error text.
- **Generated CSS classes in OrangeHRM.** Where possible the locators use stable attributes (`name`, `type`). Two locators use `oxd-` class names because the page offers nothing better. They are isolated in the locator files so a change means one edit.
- **Demo credentials.** The valid-login case reads its credentials from configuration, with the public demo values as defaults. In a real project these would come from a secret store and have no default.
- **Hand-written schemas.** The contract schemas are written by hand from the published Swagger definition. In a real project I would generate them from the OpenAPI document, or use consumer-driven contract tests (Pact), so the schema cannot drift from the specification.
- **Reporting.** A small JUnit listener builds a self-contained HTML report and opens it after the run. For a team, I would use Allure for history and trends.
- **Scope.** The brief asked for one API test and one UI test in about an hour, so breadth is described in section 3 and not implemented.
- **Not yet covered:** parallel execution, cross-browser runs, a CI pipeline, and retries for flaky infrastructure. All are supported by the structure and would be the next steps.
