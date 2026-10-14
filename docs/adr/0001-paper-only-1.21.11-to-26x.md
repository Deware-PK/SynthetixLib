# 0001. Paper only, minimum 1.21.11, must run on 26.x

- Status: Accepted
- Date: 2026-10-14

## Context
Target buyers run Paper (or forks). Supporting Spigot means losing Brigadier, Dialog API, lifecycle
events, Adventure-native API and Folia. 1.21.11 is the last 1.21 release; 26.x is current.

## Decision
Paper (and Paper forks) only. Lowest supported version 1.21.11. Every release must also run on the
latest 26.x. We compile against `paper-api:1.21.11-R0.1-SNAPSHOT` (never 26.x) so we cannot
accidentally use API that is missing on 1.21.11.

## Consequences
- API removed/changed in 26.x is only detected at runtime → `smokeTest` runs both versions (ADR 0010).
- Version-specific behaviour, if ever needed, must be detected through Paper API, not NMS.
- Dropping 1.21.11 later = new ADR (and lets us move to Java 25, ADR 0002).
