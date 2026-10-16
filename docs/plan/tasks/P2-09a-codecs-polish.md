# P2-09a Codec polish (review findings)
Status: done · Approved: yes · Model: sonnet · Effort: medium
Module: synthetixlib-config · Depends on: P2-09 · Design: config.md §3, §3b

## Prompt (paste into Claude Code)
Read AGENTS.md and docs/plan/tasks/P2-09a-codecs-polish.md, then implement the task.
Run `gradlew.bat check smokeTest`, tick the boxes, fill in "Result", set Status: review. Do not commit.

## Findings to fix (from planner review of P2-09)
1. **Generic self-referencing records recurse forever.** `record Tree<T>(T value, List<Tree<T>> children)` decoded
   as `new TypeToken<Tree<Zone>>() {}`: `TypeRef.member()` binds `T` to `TypeRef(T, {T->Zone})` instead of
   `TypeRef(Zone)`, so each level builds a new, never-equal `TypeRef` (bindings nest one level deeper each time),
   the `pending` cache never hits and resolution ends in `StackOverflowError`. Fix: normalize in `TypeRef` -
   a type argument that is a type variable bound in the current scope is replaced by its binding (also inside
   wildcards' upper bounds), so equal Java types give equal `TypeRef`s. Add tests: `Tree<Zone>` round trip with
   3 levels, and `resolvingTheSameTypeIsStable` for a generic type reached through two different paths.
2. **Sealed values lose their `type` key when encoded by runtime class.** `codecs.encode(reward)`,
   `codecs.toJson(reward)`, `codecs.encode(List.of(reward))` write no `type`, so
   `codecs.fromJson(codecs.toJson(reward), Reward.class)` fails. Fix in `CodecResolver.encodeDynamic`: if the
   runtime class is a record with exactly one top-most sealed ancestor (walk `getInterfaces()` / superclass while
   `isSealed()`), encode it with that ancestor's codec. Several unrelated sealed roots -> keep current behaviour
   (no `type`) and say so in the Javadoc. Also: `RecordCodec` of a record that is a sealed leaf silently ignores a
   `type` key equal to its own name (no "unknown key" warning), so `decode(tree, ItemReward.class)` works on
   such a tree. Replace test `encodingASealedLeafWithoutItsInterfaceHasNoDiscriminator` accordingly.
3. **Type name depends on the declared root.** With `Shape permits Round, Angular` and
   `Angular permits SquareShape`, `SquareShape` is `square` as a `Shape` but `square-shape` as an `Angular`, so
   data written as `List<Shape>` cannot be read as `List<Angular>`. Fix: compute the name per leaf, independent of
   the root: strip the simple name of the nearest sealed ancestor whose name is a suffix (direct parent first,
   then upwards); `@TypeName` still wins. Cache it per class. Test both roots give the same name.
4. **Internal types look public.** `mapping.TypeRef` and `mapping.CodecResolver` are `public` for the `codec`
   package. Annotate both classes `@org.jetbrains.annotations.ApiStatus.Internal` (same as core) and say
   "internal, do not use" in their Javadoc.
5. **`@Nullable` from JetBrains is silently ignored** (CLASS retention, invisible to reflection), which gives a
   confusing `missing` error. Do not add support; document on `Codecs` and in config.md §1 rules:
   "use `org.jspecify.annotations.Nullable` (or `Optional<T>`); `org.jetbrains.annotations.Nullable` is not
   visible at runtime".

## Accepted as is (no change)
Extra public API listed in P2-09 Result, non-negative `Duration` with `us`/`ns` and no bare numbers, leniency
rules, `Map<String,T>` only, public records only, Gson streaming JSON, `Problem(path, message)`.
P2-11 adds the per-field default fallback (see that task).

## Acceptance criteria
- [x] 1-5 fixed with the tests named above
- [x] no rule weakened, ArchUnit green
- [x] `gradlew.bat check smokeTest` green

## Verify
gradlew.bat check smokeTest

## Result
All 5 findings fixed. `gradlew.bat check smokeTest` green (Paper 1.21.11 and 26.x, selftest 37/37).
- TypeRef: `normalize()` replaces bound type variables / wildcard upper bounds by their binding; classes carry no bindings.
  Tests: `genericSelfReferencingRecordsResolve` (Tree<Zone>, 3 levels), `resolvingTheSameGenericTypeThroughTwoPathsIsStable`.
- encodeDynamic: records with one top-most sealed ancestor encode through it (several roots -> no `type`, in Javadoc).
  `RecordCodec.markSealedLeaf` makes a `type` equal to its own name not an unknown key.
  Replaced the old test with `encodingASealedLeafByRuntimeClassKeepsItsDiscriminator` + `aSealedLeafAcceptsItsOwnTypeKey`.
- `SealedCodec.nameOf(leaf)` is root-independent (nearest sealed ancestor suffix); test `typeNamesDoNotDependOnTheDeclaredRoot`.
  Not cached in a static (ArchUnit); computed once per cached SealedCodec.
- `@ApiStatus.Internal` on TypeRef and CodecResolver. JetBrains `@Nullable` documented in `Codecs` Javadoc and config.md §1.
