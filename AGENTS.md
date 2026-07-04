# AGENTS.md

## General Principles

* Prioritize readability over cleverness.
* Write code that is simple, maintainable, and easy to understand.
* Prefer clear names for classes, methods, variables, and tests.
* Avoid unnecessary abstractions unless they clearly improve maintainability.
* Read `TASK.md` to understand the project's goals and requirements.
* Read `ARCHITECTURE.md` to understand the project's architecture and design.

## Java / Spring Boot Style

* Use records for DTOs.
* Never expose JPA entities directly through API responses.
* Map entities to DTOs before returning data from controllers or services.
* Keep controllers thin.
* Put business logic in services.
* Use constructor injection instead of field injection.

## Database and Persistence

* Avoid N+1 queries.
* Use fetch joins, entity graphs, projections, batch fetching, or explicit queries where appropriate.
* Be careful when adding lazy relationships that may trigger unexpected database calls.
* Do not make broad persistence changes without considering performance impact.

## Testing Requirements

* Add tests for any accompanying code change.
* Add regression tests for bug fixes or behavior changes to ensure existing functionality does not break.
* Include unit tests for service and business logic.
* Include integration tests when changes affect persistence, APIs, security, or framework behavior.
* Do not remove or weaken existing tests unless there is a clear reason.

## Debugging and Problem Investigation

* When investigating a bug or trying to determine why a feature, function, method, endpoint, service, test, or other behavior is not working as intended, do not immediately implement a fix.
* First, investigate and identify the most likely root cause of the problem.
* Before making any code changes, present:

  * The identified or suspected problem.
  * The evidence supporting that diagnosis.
  * One or more possible solutions.
  * The tradeoffs or risks of each solution when relevant.
  * The recommended solution and why it is preferred.
* Wait for approval before implementing the proposed fix unless the task explicitly instructs you to investigate and fix the issue without confirmation.
* If the root cause is uncertain, clearly distinguish confirmed facts from hypotheses.
* Do not present speculation as a confirmed diagnosis.
* After approval, implement the smallest reasonable fix and add appropriate regression tests.

## Change Discipline

* Make the smallest reasonable change needed.
* Do not introduce unrelated refactors.
* Do not add new dependencies unless necessary.
* Explain significant design or architectural decisions.
* Preserve existing behavior unless the task explicitly requires changing it.

## Local Environment Notes

* On this machine, Maven commands should override `JAVA_HOME` for the process because the persisted value is quoted and breaks Windows batch scripts. Use `$env:JAVA_HOME='C:\Program Files\OpenJDK\jdk-25'` before running `mvn` or `mvnw.cmd`.
