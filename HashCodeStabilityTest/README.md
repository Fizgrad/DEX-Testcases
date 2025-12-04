# HashCodeStabilityTest

Stress test that allocates a wide range of object types, caches their initial
`hashCode` (and `identityHashCode`), then periodically replays the hash
calculation to ensure it never drifts for a live object.

## Build & Run

```bash
make -C HashCodeStabilityTest
dalvikvm64.sh HashCodeStabilityTest/dex/HashCodeStabilityTest.dex [runSeconds]
```

`runSeconds` is optional (default 180).

## What it does

- Allocates strings, primitive arrays, object arrays, maps/sets, immutable
  lists, UUID/time objects, read-only `ByteBuffer`s, and custom POJOs.
- Stores the first hash code plus `System.identityHashCode` for up to 25k live
  objects; older entries are evicted to avoid OOM.
- Re-verifies every tracked object roughly every 1.5s; logs heap usage and the
  top allocation mix every ~4s; throws if any hash code or identity hash
  differs from the cached value.
