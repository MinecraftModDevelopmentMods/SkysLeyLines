# Player energy pools

Requires Sky’s Ley Lines 0.5.5 or later. The Java API is still version 2.

A child mod decides which currencies each player can hold. A new player has an empty set of pools. A technomancer can have aether while a battle mage has mana, or a character can have both. Each pool holds whole converted units with its own capacity. Players cannot hold raw magic. Their units leave the nexus account when withdrawn and occupy no nexus capacity. Recharging a player draws from currency already converted at that nexus.

## Java quick start

Acquire the service on the server thread. Call this from your progression logic when a player earns their aether pool. Register the currency through the normal paradigm registry first.

```java
var service = LeyLinesApi.playerPower(player.serverLevel().getServer());
var aether = ResourceLocation.parse("example:aether");
var source = new PowerRequest.Consumer(
        ResourceLocation.parse("example:class_progression"), Optional.of(player.getUUID()));
var opened = service.configure(player.getUUID(), aether,
        new PlayerPowerService.Settings(true, 1000, MagicAmount.fraction(3, 2)), source);
```

The last argument to `Settings` sets the optional automatic recharge speed. Use `null` to turn it off. This example attempts to draw an average of 1.5 aether per server tick while the player is at an eligible nexus with converted funds. It maintains one automatic job per pool and tries again at most once a second when supply or access is unavailable. Child mods can also register currency-level timing, range, and death defaults using `LeyLinesApi.registerPlayerEnergyDefaults(currency, policy)` during setup.

| Method | What it does |
| --- | --- |
| `inspect(uuid)` | Reads an immutable map of configured pools with effective settings, active state, and units. Works for offline UUIDs. |
| `configure(uuid, currency, settings, source)` | Opens a pool or changes its requested settings. Disabling keeps its units. |
| `setBalance` / `adjust` | Trusted progression or admin changes. Negative adjustments can drain dormant pools. A positive adjustment needs an active pool with room. |
| `spend` | Deducts a positive whole number of units from an active pool. Use the successful receipt before applying gameplay effects. |
| `withdraw(player, currency, units, timing, source)` | Moves converted units from the nexus at the player’s position into the player pool. |
| `deposit(player, currency, units, timing, source)` | Moves converted units from the player into the nexus if it has room at the saved conversion rate. |
| `transfer(from, to, currency, units, timing, source)` | Moves converted units between online players. Both need the currency. |
| `convert(level, pos, currency, raw, timing, source)` | Converts exact raw magic at a nexus into that nexus’s currency account. |
| `job(id)` / `cancel(id)` | Reads or cancels a timed job. Cancellation leaves completed tranches in place. |

Removing a currency’s mod, disabling a pool, or lowering its capacity retains the units. An over-capacity pool can spend its balance if it is active. It can receive units again once it has room. Nexus rate migrations continue to re-denominate nexus balances. Wallets and drops are already-issued currency units and retain their unit counts.

## Timing, receipts, and protection

Use `Timing.instant()` for one all-or-nothing move, `Timing.duration(40)` to spread the total across 40 server ticks, or `Timing.rate(MagicAmount.fraction(3, 2))` for an exact throughput. The rate is currency units per tick for transfers, and raw magic per tick for conversion. A fractional rate carries its remainder to the next tranche. A conversion debits only the raw amount represented by whole output units. If a timed request ends with a fractional output remainder, that raw stays in the nexus and the job’s `completed` reports the exact amount used. An instant conversion requires integral output.

Starting a job reserves no funds or room. A timed job rechecks endpoints before each tranche. If funds or room run short, it moves what fits and ends with the reason. Nexus jobs are bound to the original nexus and require its chunk and the interaction position to remain loaded. A player can move within the eligible area. Moving away, changing dimension, disconnecting or dying ends the job. Transfers between players may start across dimensions by default, then stop if either changes dimension. An overloaded scheduler can take longer than the requested duration. Jobs end when the server stops. The last 1,024 terminal results are retained for the running server session.

Listen to `PlayerPowerEvent.Pre` on the NeoForge game bus to cancel a proposed mutation. Its receipt includes the source, currency, amount, before/after pool values, optional nexus readings, and the interaction position when applicable. `PlayerPowerEvent.Post` follows a committed mutation. `JobFinished` reports the terminal reason and amount completed. Nexus withdrawals also run the existing nexus SPEND events with the actual player position. Conversions use the existing nexus CONVERT events. Read queries can run in callbacks. To perform another mutation in response, schedule it for a later server tick.

## Pack and server policy

The order is child code, data pack, instance file, then world file. A layer only replaces fields it specifies. Within a file its currency rules override its defaults. Rules tune pools that a child already configured.

- A data pack can define aether in `data/example/player_energy/aether.json`. Its root is the single rule object.
- Instance policy goes in `config/skysleylines-player-energy.json`.
- World policy goes in `<world>/serverconfig/skysleylines-player-energy.json`. Use the same file format as the instance policy.

```json
{
  "defaults": {
    "sameDimension": true,
    "maxDistance": 32,
    "honorKeepInventory": true
  },
  "currencies": {
    "example:aether": {
      "capacity": "2500",
      "autoRate": "3/2",
      "transferRateLimit": "5",
      "conversionRateLimit": "1/8",
      "instant": false,
      "death": "DROP",
      "ownerOnly": true,
      "dropLifetime": 6000
    }
  }
}
```

`enabled`, `capacity`, and `autoRate` override the child’s pool settings. Set `autoRate` to JSON `null` to turn off automatic recharge. Rate limits are positive exact strings. `instant` controls the new player energy and conversion methods. A positive `maxDistance` is a three-dimensional block distance and requires both players to be in the same dimension. Zero removes the distance limit. A malformed policy stops startup. On `/reload`, an invalid policy is logged and the previous one remains in force. A successful reload that changes policy ends active jobs with `RELOADED`.

## Death and saved worlds

Default death policy is `DROP`. Active balances are emptied and leave collectible energy where the player died. Walk near the floating shards with a compatible pool to collect them. They move only as much as fits, and leftovers stay until collected or expired. By default any eligible player can pick them up, their lifetime is 6,000 server ticks, and `keepInventory` keeps the player’s balance. Other choices are `KEEP` and `LOSE`. `ownerOnly` restricts a drop to its owner. Dormant balances remain in the wallet in every mode.

On the first persistent pool change, the mod backs up the current nexus ledger and upgrades `<world>/data/skysleylines.dat` to schema 4. Player balances and drops live in that same file. Dropped entities are views of the saved balances and reappear when their chunks are loaded. Clients and servers must use matching builds. Back up the world before upgrading. Older builds cannot read schema 4.

## KubeJS

Define the currency through `StartupEvents.init` as described in the [pack maker guide](MODPACKS.md). This example assumes `kubejs:survey_aether` is already defined.

```js
const surveyAether = 'kubejs:survey_aether'
const surveyPower = 'kubejs:survey_progression'

PlayerEvents.loggedIn(event => {
  const player = event.player
  const pools = LeyLines.inspectPlayer(player).pools
  if (!pools[surveyAether]) {
    LeyLines.configurePool(player, surveyAether, true, '1000', '3/2', surveyPower)
  }
})

ItemEvents.rightClicked('minecraft:spyglass', event => {
  if (event.hand !== 'MAIN_HAND') return
  const paid = LeyLines.spendPool(event.player, surveyAether, '10', surveyPower)
  if (paid.succeeded) LeyLines.grantVision(event.player, surveyPower, 1200, 'skysleylines:crackle')
})

LeyLinesEvents.playerPoolEmpty(event => {
  console.info(event.playerId + ' spent their last ' + event.currency)
})
```

Use `inspectPlayer(player)` or `inspectPlayer(server, uuid)`, `configurePool(player, currency, enabled, capacity, autoRate, source)`, `setPoolBalance(player, currency, units, source)`, `adjustPool(player, currency, signedDelta, source)`, and `spendPool(player, currency, units, source)`. Use decimal strings for whole amounts and capacities, and exact strings such as `'3/2'` for rates. Pass `null` for no automatic recharge.

For transfers use `withdrawToPlayer(player, currency, units, schedule, source)`, `depositFromPlayer(player, currency, units, schedule, source)`, and `transferPlayers(from, to, currency, units, schedule, source)`. For raw conversion use `scheduleConversion(level, x, y, z, currency, raw, schedule, source[, actor])`. Pass `'instant'`, `'duration:40'`, or `'rate:3/2'` for the schedule. Timed submissions return `status: 'RUNNING'` and a `jobId`. Read progress with `energyJob(server, jobId)` or cancel with `cancelEnergyJob(server, jobId)`. Balances, rates, and completed amounts are returned as exact strings.

`LeyLinesEvents.playerPoolChanged` provides `playerId`, `currency`, `before`, `after`, `receipt`, and `server`. `playerPoolEmpty` has the same fields and fires only when positive units become zero. `energyJobFinished` provides `job` and `server`. These are committed notifications. Use a later server tick to initiate a new transaction from an event.

