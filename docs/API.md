# Public API 2

Use `zone.moddev.mc.skysleylines.api` from the ordinary mod JAR. Sources and Javadocs accompany it. Use the [integration quick start](INTEGRATION.md) to set up the dependency and choose an entry point.

| Type or call | Contract |
| --- | --- |
| `LeyLinesApi.forLevel(ServerLevel)` | Returns a context with the saved `profile()`, the natural `field()`, and the level-bound `power()` service. Obtain it on the server thread. |
| `LeyFieldSampler.sampleColumn(int x, int z)` | Returns the natural ground height, planar strength, and any local nexus. Reuse `naturalStrengthAt(int y)` for multiple heights. |
| `LeyFieldSampler.sample(int x, int y, int z)` | Returns a `LeySample` at one position. Use `active()` to check whether the field is available and `canDrawPower()` to check nexus eligibility. |
| `LeyFieldSampler.nearestNexus(int x, int z, int radius)` and `nearestLine(int x, int z, int radius)` | Return optional planar readings. These searches can find sites beyond the current height band. |
| `NexusPowerService.quoteAt(int x, int y, int z)` | Returns an optional immutable `NexusSnapshot` for the eligible nexus. |
| `NexusPowerService.transact(PowerRequest)` | Runs one all-or-nothing conversion, spend, or return. The three convenience methods below delegate to this operation. |

Each context belongs to its level. Reacquire it after a world reload. The retained field sampler supports worker reads, while the power service and currency metadata require the server thread.

## Register a paradigm

Register in `FMLCommonSetupEvent.enqueueWork`. Definitions freeze at load complete. Namespaced paradigm and currency IDs must be globally unique. This is a library catalogue, not a Minecraft block/item registry.

```java
static final ResourceLocation AETHER = ResourceLocation.parse("technomancy:aether");
static final ResourceLocation ELECTRIC = ResourceLocation.parse("technomancy:electric");

// In common setup's enqueueWork; myConfig is your typed child-owned config object.
var paradigm = LeyLinesApi.paradigms().register(new Paradigm<>(
    ResourceLocation.parse("technomancy:technomancy"), myConfig,
    List.of(new Paradigm.Currency(AETHER, () -> MagicAmount.of(1000)),
            new Paradigm.Currency(ELECTRIC, () -> MagicAmount.of(10000)))));
```

The paradigm groups shared magic settings and an optional typed extension. The child owns its unrelated configuration storage and validation. Ratio suppliers may read child configuration; Ley Lines evaluates them once per server start after server configs load. They never run in transactions or worldgen loops. Existing worlds retain saved ratios until an operator explicitly migrates them. Adding a new currency gives it a zero balance everywhere.

## Convert, spend, and return

```java
// Server thread, actual machine position. Do not retain across world/server lifecycles.
var ley = LeyLinesApi.forLevel(serverLevel);
var power = ley.power();
var plant = new PowerRequest.Consumer(ResourceLocation.parse("technomancy:aether_plant"));

var converted = power.convertAt(x, y, z, AETHER, MagicAmount.of(5), plant);
// Success atomically removes 5 raw and adds 5000 aether to this nexus's account.

var spent = power.spendAt(x, y, z, AETHER, 100, plant);
if (spent.succeeded()) {
    // Apply your machine's effect once, after successful payment.
}
var returned = power.returnToRawAt(x, y, z, AETHER, 1000, plant);
// Success removes 1000 aether and restores exactly 1 raw at the saved rate.
```

Every paradigm, player, and machine shares one account per nexus. Consumers may use currencies they understand from any paradigm. Raw magic is a conversion reserve, not a directly spendable currency. There are no per-player wallets or per-currency capacities.

`raw + sum(currency units / unitsPerRaw)` is the total charge. It cannot exceed the strength-dependent nexus capacity. Conversion and return preserve this total; spending reduces it. Recovery adds raw into the remaining space at `capacity / rechargeTicks` per Overworld game tick. It works while chunks are unloaded, without wall-clock/offline recovery or a per-node tick loop. Elapsed recovery is settled before a transaction, preventing retroactive recovery into space that spending just freed.

`MagicAmount` is an exact nonnegative rational with `of(long)` and `fraction(long,long)` helpers. Rates must be positive. Currency balances are whole nonnegative signed-long units. Currency IDs and each reduced rate or saved raw component must fit in a 65,535-byte NBT string. Conversion must produce whole currency units. A fractional output returns `NON_INTEGRAL_OUTPUT`, and a result that exceeds a storage limit returns `OVERFLOW`. Both leave balances intact. Returning a currency unit may restore a fractional raw amount exactly.

`quoteAt` returns an immutable nexus/account snapshot or empty outside a usable nexus field. `account.raw()`, `account.total()`, and `account.currency(id)` expose exact raw, total raw-equivalent, and currency balances. Missing currency entries mean zero. `rates()` gives the saved effective world rates, including retained inactive currencies. Quotes are projections, not reservations: they create no account, emit no event, and never dirty data.

`LeyLinesApi.currencyInfo(level)` returns an immutable map of saved rates and active flags on the server thread. `NexusSnapshot.rechargeTicks()` reports the effective refill interval, including a server override. `Context.profile().rechargeTicks()` is the base interval saved when the world first initialized.

Transactions select the nearest eligible nexus, with canonical identity breaking exact-distance ties. They never split a request or fall through to another nexus when funds are insufficient. Lines alone cannot supply power; horizontal and vertical boundaries apply. Untouched nodes start fully raw.

`PowerResult` reports explicit failure status or an immutable successful receipt containing request, nexus, account revision, and before/after snapshots. A failure makes no persisted balance change. A receipt is not an external-effect rollback mechanism or a durable deduplication key. Consumers must arrange their own machine effects around payment; do not retry a successful payment because an observer had trouble.

| Status | Meaning |
| --- | --- |
| `SUCCESS` | The ledger committed the operation and returned a receipt. |
| `NO_NEXUS` | The position falls outside a usable nexus field. |
| `INVALID_AMOUNT` | The amount is zero, or a spend or return specifies fractional units. The spend and return helpers also reject negative units. |
| `UNKNOWN_CURRENCY` | The world has no saved rate for the ID. |
| `INACTIVE_CURRENCY` | The currency is retained but unregistered. A return to raw can still use its saved rate. |
| `INSUFFICIENT_RAW` | The nexus lacks the requested raw magic. |
| `INSUFFICIENT_CURRENCY` | The requested currency balance is too small. |
| `NON_INTEGRAL_OUTPUT` | The conversion would produce a fractional currency unit. |
| `OVERFLOW` | The operation exceeds an exact storage or arithmetic limit. |
| `CANCELLED` | A pre-transaction listener vetoed the payment. |
| `REENTRANT` | The call attempted a nested ledger mutation. |
| `CHECK_FAILED` | A pre-transaction listener raised an exception. Check the server log. |

## Checks and notifications

Listen on `NeoForge.EVENT_BUS` for `NexusTransactionEvent.Pre` and `.Post`. Both provide the server level and immutable request/receipt data. Pre is cancellable and runs after validation but before commit. Include an actor UUID in `PowerRequest.Consumer` when applicable so protection integrations can make access decisions. The caller still owns the truth of its identity, position, and permissions; this is a trusted Java API with no raw network transaction endpoint.

Pre cancellation or a throwing pre listener aborts payment. Post is emitted once after balances commit and data is marked dirty. A throwing post listener is logged and the caller still receives success; delivery to other listeners depends on the event bus. All callbacks run synchronously on the server thread. Quotes are allowed, but nested ledger mutations, including those for another nexus, are rejected throughout dispatch. Ordinary failure statuses do not emit a post event.

`RateMigrationEvent.Pre` and `.Post` surround operator-driven whole-world ratio migration with the same mutation restrictions. There is no growing transaction journal: durability follows normal Minecraft saves.

## Natural field and optional OreSpawn integration

```java
// In a server-side LevelEvent.Load listener, before spawn preparation.
LeyFieldSampler sampler = LeyLinesApi.forLevel(serverLevel).field();
// Publish the sampler keyed by ServerLevel through a concurrent map.
// Drop the entry on LevelEvent.Unload and look it up from the generation worker.
LeyColumn column = sampler.sampleColumn(blockX, blockZ);
double strength = column.naturalStrengthAt(blockY);
boolean eligibleForMysticalOre = column.active() && strength >= 0.6;
```

Reuse a column outside candidate-height loops. `naturalStrengthAt` is allocation-free with no terrain reads, config access, registry lookup, or callbacks. Column acquisition caches generator base height and geometry, never requests chunks or reads live blocks. Natural strength remains independent of accounts, depletion, conversion, player presence, and generation order.

`sample` supplies natural/line/nexus strengths, vertical factor, eligible nexus, distances, and advisory `dimensionalResistance() = 1 - naturalStrength`. Bounded planar nearest searches accept radii `1..min(4096,2*savedSpacing)`; inactive dimensions return inactive/empty readings. Search sites can lie over void columns, so check column activity. Overworld only remains active. No seed/topology synchronization or portal policy is supplied.

For optional integration, check `ModList.get().isLoaded("skysleylines")` before loading a dedicated integration class with API references. Compile production integrations against a matching released artifact and declare a compatible optional dependency. Acquire the sampler during `LevelEvent.Load`; `ServerStartingEvent` runs after spawn preparation on this target. If the optional mod is absent, use the consumer’s ordinary generation rule. The disposable worldgen probe exercises this pattern independently of OreSpawn.

## Ley vision grants

Since Sky's Ley Lines 0.4, spells, potions, or equipment can grant a player the same local view as the operator command. Call on the server thread with the connected player's `ServerPlayer`.

```java
static final ResourceLocation MAGIC_VISION = ResourceLocation.parse("technomancy:magic_vision");

LeyVision.grant(player, MAGIC_VISION, 20 * 60); // One minute at 20 ticks per second.
// If the effect is dispelled before it expires:
LeyVision.revoke(player, MAGIC_VISION);
```

For a styled effect, use a built-in ID (`skysleylines:river`, `skysleylines:crackle`, `skysleylines:sparkles`, or `skysleylines:glow`) or register a custom variant during mod setup. Use a namespaced ID you own and register it before load completion. Use Sky’s Ley Lines 0.5.0 or later when calling the style API.

```java
static final ResourceLocation GOGGLE_STYLE = ResourceLocation.parse("technomancy:goggle_style");

// During mod setup
LeyVision.registerStyle(GOGGLE_STYLE, new LeyVisionStyle(
        LeyVisionStyle.Pattern.CRACKLE, 0xffb54b, 0xff683b, 0.85f, 1.25f));

// When the player wears the goggles
LeyVision.grant(player, MAGIC_VISION, 20, GOGGLE_STYLE);
```

Use RGB colours in `0xRRGGBB` form, opacity between `0.05` and `1.0`, and scale between `0.25` and `1.5`. Each colour tints its corresponding line or nexus effect. The client receives the resolved style from the server. The newest active styled source wins. Renewing an active source keeps its activation order. The server default applies when vision is active and no styled source remains.

Use a distinct namespaced source for each effect that should expire independently. Granting again replaces that source's expiry with the new duration from the current tick. Vision remains visible while any grant or operator toggle is active. A child mod owns the trigger, permission checks, and any power cost. Grants last for the current player session and end on logout or respawn. A grant keeps counting down in other dimensions, where the current layout provides no visible lines.

The server sends nearby clipped segments and crossings only to the viewer. The client uses terrain already loaded there to place the effect. The display follows the planar field, while exact vertical strength and nexus eligibility come from `LeyFieldSampler`. The visual width, water surface height, and plumes are presentation choices.

[Java integration quick start](INTEGRATION.md) · [Operator commands](SERVER_GUIDE.md) · [KubeJS bindings](MODPACKS.md)
