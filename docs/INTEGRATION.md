# Java integration quick start

Compile against the ordinary Sky’s Ley Lines JAR for NeoForge 1.21.1. Install that mod separately in the runtime. If your integration is optional, declare it as optional in `META-INF/neoforge.mods.toml` and use a dedicated integration class. Check `ModList.get().isLoaded("skysleylines")` before loading that class so your mod can start when the dependency is absent.

Use the ordinary artifact from MMD Maven. This example pins the version documented here.

```groovy
repositories {
    maven {
        url = 'https://maven.moddev.zone/releases/'
        content { includeGroup 'zone.moddev.mc.skysleylines' }
    }
}

dependencies {
    compileOnly 'zone.moddev.mc.skysleylines:SkysLeyLines:0.5.6.121012'
}
```

For a candidate awaiting publication, you can temporarily compile against the exact local JAR using `compileOnly files('libs/SkysLeyLines-0.5.6.121012.jar')`. Stage the ordinary dependency JAR as a mod in your development run’s `mods` directory. For release, declare a version range that covers the API you use and the versions you tested. Replace `yourmod` with your mod ID and use `type="required"` if your mod relies on Ley Lines to function.

```toml
[[dependencies.yourmod]]
modId="skysleylines"
type="optional"
versionRange="[0.5.6.121012]"
ordering="AFTER"
side="BOTH"
```

Choose an entry point based on the job.

| Job | Entry point | When to call it |
| --- | --- | --- |
| Read natural strength for ores, structures, or rituals | `LeyLinesApi.forLevel(level).field()` and `LeyFieldSampler.sampleColumn(x, z)` | Acquire the sampler on the server thread during level load. Its retained sampler supports worker reads. |
| Find nexus sites in a structure callback | `LeyLinesApi.worldgenSampler(randomState)` and `LeyFieldSampler.nexusesInChunk(chunkX, chunkZ)` | Query on the generation worker. See the [worldgen guide](WORLDGEN_AND_PINS.md). |
| Register a paradigm and currencies | `LeyLinesApi.paradigms().register(...)` | Common setup via `enqueueWork`, before registration freezes at load complete. |
| Inspect balances or pay for a machine action | `NexusPowerService.quoteAt`, `convertAt`, `spendAt`, or `returnToRawAt` | On the owning server thread. Use the position where the action happens. |
| Read saved rates and active flags | `LeyLinesApi.currencyInfo(level)` | On the server thread. |
| Give players personal pools or move energy over time | `LeyLinesApi.playerPower(server)` | On the server thread. See [player energy](PLAYER_ENERGY.md). |
| Show Ley Vision to a player | `LeyVision.grant` or `revoke` | On the connected player’s server thread. Register custom styles during mod setup. |
| Enforce protection rules or observe a completed transaction | `NexusTransactionEvent.Pre` or `.Post` | Listen on `NeoForge.EVENT_BUS`. Include the player UUID in the request when one is responsible. |

For a first machine, register one currency, convert a small amount of raw magic at the machine position, and have a second machine spend from the same nexus. Act on the returned status. Apply the consuming machine’s effect once after a successful spend. Use the before and after snapshots to show the exact payment.

For an optional ore feature, acquire the sampler in a server-side `LevelEvent.Load` handler before spawn preparation. Publish it in a concurrent map keyed by `ServerLevel` and remove the entry on `LevelEvent.Unload`. A worker can retrieve that sampler and reuse one column for several candidate heights. Keep the account and transaction service on the server thread.

See the [public API reference](API.md) for complete signatures, result codes, thread rules, and examples. Pack scripts use the [KubeJS guide](MODPACKS.md).
