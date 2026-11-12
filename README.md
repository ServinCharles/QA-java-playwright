# QA Technical Test: Playwright Test Automation Framework (Java)

[![Tests](https://github.com/ServinCharles/QA-java-playwright/actions/workflows/tests.yml/badge.svg)](https://github.com/ServinCharles/QA-java-playwright/actions/workflows/tests.yml)

API tests against the Swagger Petstore and UI tests against OrangeHRM, built on Playwright for Java and JUnit 5.

## Prerequisites

- JDK 17 or newer
- Maven 3.8 or newer
- Internet access to `petstore.swagger.io` and `opensource-demo.orangehrmlive.com`

## First-time setup

Install the Playwright browsers once. Chromium is the default. Add Firefox and WebKit if you want to run cross-browser:

```bash
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install --with-deps chromium firefox webkit"
```

## Running the tests

```bash
mvn test                  # API and UI tests, then the report opens in your browser
mvn test -Dgroups=api     # API tests only
mvn test -Dgroups=ui      # UI tests only
```

Cross-browser. The UI tests run on Chromium by default. Set `BROWSER` to pick another engine:

```bash
BROWSER=firefox mvn test -Dgroups=ui
BROWSER=webkit mvn test -Dgroups=ui
```

WebKit is the engine behind Safari, so it is the closest check to Safari that runs on any machine. The report header shows which browser was used.

Retries. Failed tests are not retried by default, so a real failure shows at once. To rerun failed tests once:

```bash
mvn test -Dtest.retries=1
```

A test that fails and then passes is shown as **FLAKY** in the report, with the reason it failed the first time.

Parallel runs. Test classes run side by side, and tests inside a class run in order. To switch that off for one run:

```bash
mvn test -Djunit.jupiter.execution.parallel.enabled=false
```

## Debugging

Playwright's UI mode does not exist for Java. These are the closest tools:

```bash
# Watch the run in a real browser window, slowed down so you can follow it
HEADLESS=false SLOW_MO=500 mvn test -Dgroups=ui

# Step through a test in the Playwright Inspector (pauses at the first browser action)
PWDEBUG=1 PLAYWRIGHT_JAVA_SRC=src/test/java mvn test -Dgroups=ui

# Open the trace of a failed test: every action, with DOM snapshots, network and console
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="show-trace target/traces/<name>.zip"

# Record your clicks as Java code and try out selectors
mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="codegen https://opensource-demo.orangehrmlive.com"
```

## Continuous integration

GitHub Actions runs the suite on every commit pushed to any branch, on every pull request, and on demand from the Actions tab. The workflow is in `.github/workflows/tests.yml` and has four jobs:

- **API tests**, run once, because they use no browser.
- **UI tests (chromium)**, **UI tests (firefox)** and **UI tests (webkit)**, run side by side. One browser failing does not stop the others.

CI reruns a failed test once (`-Dtest.retries=1`). If it then passes, the job is green and the report marks the test as flaky.

Each job uploads its own artifact (`test-report-api`, `test-report-ui-chromium`, and so on) containing the HTML report, the Surefire results, and any failure screenshots, videos and traces. Download it from the run's summary page and open `test-report/index.html`.

Both systems under test are public demo sites, so a run can fail when one of them is down. Check the report before treating a red build as a code problem.

## Configuration

Each value resolves in this order: system property, then environment variable, then default.

| Variable | Default | Purpose |
| --- | --- | --- |
| `BROWSER` | `chromium` | Browser engine for the UI tests: `chromium`, `firefox` or `webkit` |
| `HEADLESS` | `true` | Run the browser without a window |
| `SLOW_MO` | `0` | Milliseconds to wait after each browser action, for watching a headed run |
| `VIDEO` | `on-failure` | `on-failure` records UI tests and keeps the video only for failures. `off` records nothing |
| `test.retries` | `0` | How many times a failed test is rerun. Pass as `-Dtest.retries=1` |
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
              UiArtifactsExtension (screenshots, traces and videos on failure)
  ui/locators/ One class per page, all selectors as constants
  ui/pages/   Page objects: actions only, no raw selectors
  ui/tests/   UI test classes (data-driven)
  data/       Typed records for test data
  support/    Config (env and property resolution), TestDataLoader (JSON reader),
              SchemaValidator (API contract checks), HtmlReportListener (report),
              ArtifactNames (file names for failure evidence)
src/test/resources/
  junit-platform.properties        Parallel execution settings
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
- **Failure evidence.** A failed UI test saves a full-page screenshot (`target/failures`), a video (`target/videos`) and a Playwright trace (`target/traces`). Passing tests keep nothing. Files are named after the test and its case number, for example `LoginTest.loginBehavesAsExpected-2`, and the report links to them.
- **Parallel-safe.** Playwright for Java is not thread-safe, so each UI test class owns its own Playwright and browser, and nothing is shared between classes.
- **Retries are visible.** A retry never hides a problem: a test that needed one is reported as flaky, not passed.
- **Reporting.** Every run writes an HTML report to `target/test-report/index.html` and opens it in the default browser, whether the run passed or failed. It lists each test with its layer (API or UI), result, time, and failure details. `HtmlReportListener` builds it inside the test JVM. Set `OPEN_REPORT=false` to stop the browser opening. It never opens when the `CI` environment variable is set. Surefire still prints results to the console.

## Notes

- The public Petstore is shared and sometimes slow, so the API tests use random pet ids to avoid clashes.
- The OrangeHRM demo can be reset, and its markup can change. If a UI test fails, check the selectors in `ui/locators` first.
