# Structure generation and nexus pins

Requires Sky’s Ley Lines 0.5.5 for NeoForge 1.21.1. A mod can use the same nexus map for structures, ore placement, and gameplay. Operators can add named nexuses to an existing world.

## Place a structure at a nexus

A structure placement or structure-start callback can use its `RandomState` on the generation worker.

```java
var field = LeyLinesApi.worldgenSampler(context.randomState());
if (field.isEmpty()) return Optional.empty();
var chunk = context.chunkPos();
var sites = field.get().nexusesInChunk(chunk.x, chunk.z);
```

The result contains every nexus centred inside that chunk, sorted by stable ID. A chunk owns the half-open range from its minimum block coordinate through the next chunk boundary. This also works at negative coordinates. Queries are immutable, use bounded geometry lookups, and never sample terrain or load a chunk. Seeded nexuses supply exact X/Z and strength. Use your structure’s terrain rules to choose Y. Authored pins also supply `authoredY()` if you want to follow their explicit elevation.

Use each nexus’s stable ID with your own salt to select the sites that should get a structure. Check the same selection in both placement and the structure-start callback. A chunk can contain multiple sites. If the structure needs one start per chunk, choose one in stable order or attach several pieces to one start. The normal biome and structure checks still apply.

The optional lookup uses the exact generator state from the callback. An unbound state returns empty. To support play without Ley Lines, declare an optional dependency, check `ModList.get().isLoaded("skysleylines")`, and keep direct API references in a class loaded only when the mod is present. Keep your ordinary placement rule for that case.

## Sample an ore candidate

For a placed feature, acquire `LeyLinesApi.forLevel(level).field()` during the server’s `LevelEvent.Load` and publish that sampler to workers. Remove it on level unload. Structure code can use the state lookup above. Sample a candidate column once and reuse its strength across heights.

```java
var column = sampler.sampleColumn(x, z);
double strength = column.naturalStrengthAt(y);
```

A cold column can compute the natural base height. Query the columns you intend to use and reuse the result where a placement checks several Y values. The account and transaction API requires the server thread.

## Pin a nexus in an existing world

The following commands require permission level 4. Place a pin in the Overworld with a unique namespaced ID and strength greater than zero through one. The position must be within build bounds over natural terrain. The Y coordinate is explicit, so a pin can sit in a cave.

```text
/skysleylines nexus pin preview pack:dragon_roost 1200 42 -480 0.85
/skysleylines nexus pin add pack:dragon_roost 1200 42 -480 0.85
/skysleylines nexus pin list
/skysleylines nexus pin remove pack:dragon_roost
```

Preview reports the capacity, nearby nexus fields that can overlap, and up to two joins to seeded ley lines within 512 blocks. The joins slope from the pin to the natural ground at the seeded line. A pin can be isolated. The world supports up to 256 active or staged pins. Two pins may share a column at different heights.

Adds and removals take effect on the next world start. After restart, the same pins appear in Ley Vision, dowsing, the Ley Meter, and worldgen queries. Generated caves and ores stay where they are. New chunks can use the active pins as sites. Natural nexus IDs stay stable. In overlapping fields, the nearest eligible anchor controls the account, with a pin taking priority at its exact anchor.

The first pin write backs up the ledger. Pin data uses schema 5 or later. Retirement removes the pin from the field and keeps its saved converted balances. Its ID stays reserved. Use the following command to move whole units to an active nexus with room. The target can receive retained currency even when its mod is absent.

```text
/skysleylines nexus pin recover pack:dragon_roost example:aether 500 1166 64 -749
```

Recovery uses the saved rate to check target capacity. The debit and credit commit together. A rate migration that would revalue currency in a retired account waits until those units are recovered. Java mods can observe or cancel the proposed move through `NexusRecoveryEvent.Pre` and observe a committed move through `NexusRecoveryEvent.Post`. Both events include the source and destination accounts and positions.

