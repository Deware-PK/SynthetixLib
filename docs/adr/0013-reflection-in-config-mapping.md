# 0013. Narrow reflection exception for config mapping

- Status: Accepted
- Date: 2026-10-14

## Context
Typed config maps consumer records to data (docs/design/config.md §1, §3b). That needs
`Class#getRecordComponents()`, generic type info and the canonical constructor (`java.lang.reflect`),
which ADR 0008 forbids.

## Decision
`java.lang.reflect` is allowed **only** in package `com.github.dewarepk.synthetixlib.config.mapping`
and only on `Class` objects passed in by the consumer. Still forbidden everywhere: `Class.forName`, class
names in strings, `setAccessible`, touching server internals. Reflection runs at load/encode time, results
(component accessors, constructors) are cached per class in the `Codecs` instance (not static).

## Consequences
ArchUnit `noReflection` gets this single package exception and a new rule forbidding `setAccessible`
everywhere. Relocation stays safe (no string class lookups).
