# Configuration and world continuity

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

## Version-one landscape

Every spacing-sized source cell emits one straight segment of length three times spacing. The seed and dimension identity hash its centre, heading, and intensity independently. Segment intersections form nexuses. Intersecting line identities give each nexus an ordered, collision-free identity. Additional nearby lines strengthen a nexus. Cache tile boundaries are not part of its identity or strength.

Line intensity is in [0.5,1), with half that value as peak line field strength. Nexus intrinsic strength averages its two crossing line intensities, plus 0.1 times each additional line intensity within the configured line width, capped at 1. Horizontal influence fades linearly to zero at the line half-width or nexus radius; the local field takes the maximum contribution. Distinct nearby intersections remain distinct nexuses rather than a traversal-dependent cluster merge.

The terrain anchor is `ChunkGenerator.getBaseHeight(OCEAN_FLOOR_WG) - 1`, not a live chunk heightmap. The planar layout depends on seed, dimension ID, settings, and layout version. Its height also depends on the world's terrain generator/settings. It ignores player buildings, digging, trees, and later decorations. Changing worldgen datapacks or generators can therefore change the vertical anchor even though the planar sites remain fixed. Empty base-terrain columns have no active field.

Strength is multiplied by `max(0,1-abs(y-naturalSurfaceY)/verticalReach)`. Thus the default reach gives half strength at +/-64 and zero at +/-128. Overworld only is active in version 1; the API is dimension-aware for future separately specified profiles.

Equipped dowsing rods turn towards the closest ley line within 256 blocks while inside this vertical band. Within two blocks of the line they align along it. They cross inside a usable nexus field, irrespective of its remaining reserves. Unsupported dimensions, void terrain, and positions outside the vertical band produce no animated signal. Only a coarse local bearing and quiet/line/nexus state are sent to the holder and nearby observers; the seed and network map stay on the server. Stale readings expire after one second. Right-click retains the separate textual search for a nexus within 512 blocks and a line within 256 blocks.

`/skysleylines vision [on|off]` controls your personal operator view. Omit the argument to toggle it. Vision draws nearby lines and nexuses across loaded terrain in the Overworld, with a distance fade out to 320 blocks. It is a survey aid and works from above the vertical field band. The command needs permission level 2. The toggle ends on logout, respawn, or loss of operator permission. Child mods can grant temporary vision independently. Neither source saves settings in the world or consumes nexus power.

## Shared accounts and save schema 2

Each node has one total capacity shared by raw and all currencies. Capacity and recharge still come from the saved world profile and intrinsic nexus strength. Converted balances occupy capacity and never recharge themselves. Only spending frees space; normal game-tick recharge then restores raw. Dowsing rods report total charge, including converted currencies.

Exact raw fractions, whole currency balances, timestamps, the global account revision, and frozen world ratios are stored in `data/skysleylines.dat`. Untouched accounts are implicitly fully raw. Only full accounts with no converted funds can be pruned; fully converted accounts must remain saved. No per-node ticking or chunk loading is used.

Schema-1 worlds upgrade automatically. The loader validates first and copies the original data file to a unique `skysleylines-schema1-*.dat.bak` before any upgrade is saved. Raw balances and recharge remainders retain their exact value. Layout version 1 and the saved profile remain unchanged. Unknown versions, malformed fields, duplicate IDs, invalid ratios, and over-capacity accounts fail before Minecraft can substitute defaults.

Child mods register currencies and rate suppliers. A world remembers each currency's effective units-per-raw ratio. Removing a child preserves its balances and occupied capacity. Inactive currencies cannot receive new conversions or ordinary spending, but their value can explicitly return to raw through the API. Reinstalling the child restores access at the saved rate.

Changing a child configuration never silently revalues existing balances. After restarting with changed settings, an operator with permission level 4 can use:

1. `/skysleylines rates` to inspect saved ratios and installed currencies.
2. `/skysleylines rates preview` to inspect proposed rates, affected account count, and up to ten projected accounts.
3. `/skysleylines rates apply <revision>` to apply that operator's preview. Intervening payments invalidate it; elapsed normal recharge is evaluated again at apply time.

Migration validates every account and creates a compressed backup of the current ledger before committing the complete rate/account replacement. Each old currency balance is re-denominated while preserving raw-equivalent value; an indivisible fractional currency remainder returns to raw. Cancellation, overflow, invalid data, stale previews, or a failed backup prevent all changes. Migration emits cancellable pre and committed post events.

`/skysleylines account` displays raw, total, capacity, recharge, and each currency balance at the operator's position. Ordinary diagnostic commands require permission level 2. There is no growing transaction history: persistence follows normal Minecraft saves. Back up worlds before upgrades; never delete ley data to bypass a load failure.
