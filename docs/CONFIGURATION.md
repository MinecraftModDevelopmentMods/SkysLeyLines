# Configuration and world continuity

Open **Mods → Sky’s Ley Lines → Config**. **New World Defaults** edits the settings that a world copies when its ley profile is first initialized. **Server Settings** is available in a local single-player world. It lets you pick the default Ley Vision style and set a recharge override. Reopen the world to apply a new recharge interval. The screen edits the server TOML that NeoForge loaded. Worlds using the instance server config share those settings. To make edits specific to one world, place a copy of the TOML in that world’s `serverconfig` folder before opening it. For a dedicated server or a LAN host, edit the server TOML with the world closed.

`config/skysleylines-common.toml` contains defaults for NEW worlds. On first server start with the mod, the complete effective profile and version identifiers are saved in `<world>/data/skysleylines.dat`. Existing worlds retain those values even when installation defaults change. `/skysleylines profile` shows the saved values.

| Setting | Default | Allowed range |
| --- | ---: | ---: |
| spacingBlocks | 2048 | 256..65536 |
| lineHalfWidthBlocks | 32 | 1..128 |
| nexusRadiusBlocks | 64 | 1..128 |
| verticalReachBlocks | 128 | 1..2048 |
| fullStrengthCapacity | 10000 | 1..1000000 |
| rechargeTicks | 24000 | 1..1728000 |

Spacing is a source-cell scale, not a guaranteed minimum distance between nexuses. Random crossings can form clusters and gaps. Changing it scales the seeded planar layout. No existing-world reset or layout-edit command is included.

The server config supplies these settings for new and existing worlds.

| Setting | Default | Accepted values | When it applies |
| --- | --- | --- | --- |
| `defaultVisionStyle` | `"river"` | `"river"`, `"crackle"`, `"sparkles"`, `"glow"` | Operator vision and unstyled effects use the server default. |
| `rechargeTicksOverride` | `0` | `0` or an integer from 1 through 1,728,000 | Read when the world opens. Zero uses the saved base interval. |

For example, use the following server TOML for Crackle vision and a three Minecraft day refill.

```toml
defaultVisionStyle = "crackle"
rechargeTicksOverride = 72000
```

## Version-one landscape

Every spacing-sized source cell emits one straight segment of length three times spacing. The seed and dimension identity hash its centre, heading, and intensity independently. Segment intersections form nexuses. Intersecting line identities give each nexus an ordered, collision-free identity. Additional nearby lines strengthen a nexus. Cache tile boundaries are not part of its identity or strength.

Line intensity is in [0.5,1), with half that value as peak line field strength. Nexus intrinsic strength averages its two crossing line intensities, plus 0.1 times each additional line intensity within the configured line width, capped at 1. Horizontal influence fades linearly to zero at the line half-width or nexus radius; the local field takes the maximum contribution. Distinct nearby intersections remain distinct nexuses rather than a traversal-dependent cluster merge.

The terrain anchor is `ChunkGenerator.getBaseHeight(OCEAN_FLOOR_WG) - 1`, not a live chunk heightmap. The planar layout depends on seed, dimension ID, settings, and layout version. Its height also depends on the world's terrain generator/settings. It ignores player buildings, digging, trees, and later decorations. Changing worldgen datapacks or generators can therefore change the vertical anchor even though the planar sites remain fixed. Empty base-terrain columns have no active field.

Strength is multiplied by `max(0,1-abs(y-naturalSurfaceY)/verticalReach)`. Thus the default reach gives half strength at +/-64 and zero at +/-128. Overworld only is active in version 1; the API is dimension-aware for future separately specified profiles.

Equipped dowsing rods turn towards the closest ley line within 256 blocks while inside this vertical band. Within two blocks of the line they align along it. They cross inside a usable nexus field, irrespective of its remaining reserves. Unsupported dimensions, void terrain, and positions outside the vertical band produce no animated signal. Only a coarse local bearing and quiet/line/nexus state are sent to the holder and nearby observers; the seed and network map stay on the server. Stale readings expire after one second. Right-click retains the separate textual search for a nexus within 512 blocks and a line within 256 blocks.

`/skysleylines vision [on|off]` controls your personal operator view. Omit the argument to toggle it. Vision draws nearby lines and nexuses across loaded terrain in the Overworld, with a distance fade out to 320 blocks. It is a survey aid and works from above the vertical field band. The command needs permission level 2. The toggle ends on logout, respawn, or loss of operator permission. Child mods can grant temporary vision independently. Grants and operator toggles last for the session and consume no nexus power.

Set `defaultVisionStyle` in `config/skysleylines-server.toml` to `"river"` (default), `"crackle"`, `"sparkles"`, or `"glow"`. For a single world, put the same file in `<world>/serverconfig/` to override the instance default. Edit the config while the world is closed. The setting applies to operator vision and unstyled grants in existing and new worlds. Styled grants can override it for the viewer while they are active. All four styles are visual overlays.

## Shared accounts and recharge

Each node has one total capacity shared by raw and all currencies. The saved world profile and intrinsic nexus strength determine capacity. Converted balances occupy capacity. Spending frees space for raw recharge, and dowsing rods report total charge, including converted currencies.

Set `rechargeTicksOverride` in `skysleylines-server.toml` to `0` to use the base world profile or to an integer from `1` through `1728000` for a custom full refill interval. Restart the world to apply it. Elapsed ticks settle at the previous rate. The mod backs up the prior ledger before a change. `/skysleylines profile` shows the base and effective values. For example, `72000` gives a three Minecraft day refill. Use `<world>/serverconfig/` for a world-specific override and `config/` for the instance default.

Exact raw fractions, whole currency balances, timestamps, the global account revision, and frozen world ratios are stored in `data/skysleylines.dat`. Untouched accounts are implicitly fully raw. Only full accounts with no converted funds can be pruned; fully converted accounts must remain saved. No per-node ticking or chunk loading is used.

Schema-1 worlds upgrade automatically. The loader validates first and copies the original data file to a unique `skysleylines-schema1-*.dat.bak` before any upgrade is saved. Raw balances and recharge remainders retain their exact value. Layout version 1 and the saved profile remain unchanged. Unknown versions, malformed fields, duplicate IDs, invalid ratios, and over-capacity accounts fail before Minecraft can substitute defaults.

Worlds without a change to the effective recharge rate continue to save as schema 2. The first effective change switches to schema 3, which records the new interval and remains in use even after a reset to the base rate. Older builds refuse schema 3. A recharge change creates a `skysleylines-before-recharge-*.dat.bak` copy if the world already has a ledger.

Child mods register currencies and rate suppliers. A world remembers each currency's effective units-per-raw ratio. Removing a child preserves its balances and occupied capacity. Inactive currencies cannot receive new conversions or ordinary spending, but their value can explicitly return to raw through the API. Reinstalling the child restores access at the saved rate.

Operators with permission level 4 can use `/skysleylines account recover <currency> <units> [x y z]` to return a specified number of inactive currency units to raw at one nexus. The command uses the saved conversion rate and normal protection checks.

Changing a child configuration never silently revalues existing balances. After restarting with changed settings, an operator with permission level 4 can use:

1. `/skysleylines rates` to inspect saved ratios and installed currencies.
2. `/skysleylines rates preview` to inspect proposed rates, affected account count, and up to ten projected accounts.
3. `/skysleylines rates apply <revision>` to apply that operator's preview. Intervening payments invalidate it; elapsed normal recharge is evaluated again at apply time.

Migration validates every account and creates a compressed backup of the current ledger before committing the complete rate/account replacement. Each old currency balance is re-denominated while preserving raw-equivalent value; an indivisible fractional currency remainder returns to raw. Cancellation, overflow, invalid data, stale previews, or a failed backup prevent all changes. Migration emits cancellable pre and committed post events.

`/skysleylines account [x y z]` displays raw, total, capacity, recharge, and each saved currency at the supplied position or the command source’s position. The handheld Ley Meter takes the same read-only snapshot at a targeted block or at your feet. The screen limits exceptionally large lists to 512 rows and marks long values with an ellipsis. The account command lists the complete readings. Ordinary diagnostic commands require permission level 2. There is no growing transaction history: persistence follows normal Minecraft saves. Back up worlds before upgrades; never delete ley data to bypass a load failure.

[Operator commands](SERVER_GUIDE.md) · [Troubleshooting](TROUBLESHOOTING.md)
