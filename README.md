# QA Technical Test: Playwright Test Automation Framework (Java)

API tests against the Swagger Petstore and UI tests against OrangeHRM, built on Playwright for Java and JUnit 5.

## Prerequisites

- JDK 17 or newer
- Maven 3.8 or newer
- Internet access to `petstore.swagger.io` and `opensource-demo.orangehrmlive.com`

## First-time setup

Install the Playwright browser (Chromium) once:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium"
```

## Running the tests

```bash
mvn test                  # API and UI tests, then the report opens in your browser
mvn test -Dgroups=api     # API tests only
mvn test -Dgroups=ui      # UI tests only
```

Headed browser for debugging:

```bash
HEADLESS=false mvn test -Dgroups=ui
```

## Configuration

Each value resolves in this order: system property, then environment variable, then default.

| Variable | Default | Purpose |
| --- | --- | --- |
| `HEADLESS` | `true` | Run the browser without a window |
| `OPEN_REPORT` | `true` | Open the HTML report in the default browser after the run |
| `PETSTORE_BASE_URL` | `https://petstore.swagger.io/v2` | API base URL |
| `ORANGEHRM_BASE_URL` | `https://opensource-demo.orangehrmlive.com/web/index.php` | UI base URL |
| `ORANGEHRM_USERNAME` | `Admin` | Login user for the valid-credentials case |
| `ORANGEHRM_PASSWORD` | `admin123` | Login password for the valid-credentials case |

The defaults are the public OrangeHRM demo credentials. Set your own values for any other environment, and never commit real passwords.

## Project structure

```
src/test/java/com/example/
  api/        PetStoreClient (HTTP client), PetApiTest (API tests)
  ui/base/    BasePage (shared actions), BaseUiTest (browser lifecycle),
              UiArtifactsExtension (screenshots and traces on failure)
  ui/locators/ One class per page, all selectors as constants
  ui/pages/   Page objects: actions only, no raw selectors
  ui/tests/   UI test classes (data-driven)
  data/       Typed records for test data
  support/    Config (env and property resolution), TestDataLoader (JSON reader),
              SchemaValidator (API contract checks), HtmlReportListener (report)
src/test/resources/
  testdata/orangehrm/login.json    Login scenarios and expected error messages
  testdata/petstore/pets.json      Petstore scenario data and expected error message
  schemas/pet.schema.json          Contract for a Pet response
  schemas/api-error.schema.json    Contract for an error response
```

## Test design

See [docs/TEST_DESIGN.md](docs/TEST_DESIGN.md) for the scenarios chosen and why, the further scenarios in priority order, and which checks belong at the API layer instead of the UI.

## Design notes

- **Page Object Model.** Tests talk to page objects, and page objects talk to `BasePage`. Selectors live only in the locator classes, so a UI change means one edit.
- **Test data is separate.** Scenarios are JSON, mapped to records. Adding a login case means adding a JSON entry, not writing Java.
- **API contract validation.** Every API response is validated against a JSON Schema (draft-07) with the networknt validator, as well as its status code and values. A failure lists every violation.
- **Error message validation.** The UI tests assert the exact "Invalid credentials" banner and the per-field "Required" messages. The API tests assert the "Pet not found" error body. Expected messages live in the data files.
- **Reusable actions.** `BasePage` holds click, fill, select, radio, checkbox, `clickButton("Continue")`, waits, and reads.
- **Credentials stay out of the data files.** The valid-login case reads its username and password from `Config`.
- **Failure evidence.** A failed UI test saves a full-page screenshot and a Playwright trace under `target/failures` and `target/traces`. Open a trace with `npx playwright show-trace <file>.zip` or the Playwright trace viewer.
- **Reporting.** Every run writes an HTML report to `target/test-report/index.html` and opens it in the default browser, whether the run passed or failed. It lists each test with its layer (API or UI), result, time, and failure details. `HtmlReportListener` builds it inside the test JVM. Set `OPEN_REPORT=false` to stop the browser opening. It never opens when the `CI` environment variable is set. Surefire still prints results to the console.

## Notes

- The public Petstore is shared and sometimes slow, so the API tests use random pet ids to avoid clashes.
- The OrangeHRM demo can be reset, and its markup can change. If a UI test fails, check the selectors in `ui/locators` first.
- This framework was written without a live run in the authoring environment. Run `mvn test` and fix any selector or timing issues before relying on the results.
