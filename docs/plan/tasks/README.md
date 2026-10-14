# Tasks

One file per task: `P<phase>-<nn>-<slug>.md`. Small enough for one agent session.
Agent: pick the lowest-numbered `todo` task, propose your approach, wait for "go", implement, verify, tick boxes.

## Template
```md
# P2-03 Scheduler facade
Status: todo | in-progress | review | done
Module: synthetixlib-core   Depends on: P2-01

## Goal
One paragraph.

## User-side API (must compile as written)
​```java
lib.scheduler().region(location).run(() -> ...);
​```

## Acceptance criteria
- [ ] ...
- [ ] unit tests for ...
- [ ] self-test `core.scheduler.*` passes on 1.21.11 and 26.x

## Verify
./gradlew check smokeTest

## Notes / out of scope
```
