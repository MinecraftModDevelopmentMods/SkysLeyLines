# Server operator guide

Install the matching Sky’s Ley Lines build on the server and its clients. For a local world, place the JAR in the instance’s `mods` directory. Check the [release requirements](RELEASE-0.5.6.md) before choosing a build.

## Survey a nexus

1. In the Overworld, run `/skysleylines locate nexus`. Click the reported coordinates in chat to copy them. The Y coordinate is the natural ground height, which can be below water.
2. Use `/skysleylines vision on` to see the crossing. Move to where you plan to put a machine and run `/skysleylines sample` and `/skysleylines account`. A visible line can have natural strength even when there is no eligible nexus account.
3. For a closer look, use the Ley Meter. Find it under **Operator Utilities** in Creative with the operator items tab enabled, or use `/give @s skysleylines:ley_meter`. Right-click a block to inspect it or aim into empty space and right-click to sample your feet. Use it again for a fresh reading.

The meter shows field strength, the eligible nexus, and a read-only account snapshot. Inactive currencies are retained in the account. Use the account command for the full report if the meter truncates long values or a large currency list.

## Command reference

Permission levels below are Minecraft command permission levels. Brackets mark optional arguments. Commands that accept a position use the source position when coordinates are omitted. An explicit position is in the source dimension.

| Command | Level | Result |
| --- | ---: | --- |
| `/skysleylines locate nexus` or `/skysleylines nearest` | 2 | Finds the nearest Overworld nexus within the lesser of 4,096 blocks and twice the saved spacing. |
| `/skysleylines vision [on\|off]` | 2 | Sets personal operator vision or toggles it when the argument is omitted. Run it as a player. |
| `/skysleylines sample [x y z]` | 2 | Shows field activity, strengths, vertical factor, resistance, and total charge if a nexus is eligible. |
| `/skysleylines account [x y z]` | 2 | Shows the eligible nexus’s raw and total charge, capacity, full refill time, and saved currency balances and rates. |
| `/skysleylines profile` | 2 | Shows the saved world profile, effective recharge interval, and capacity multiplier. |
| `/skysleylines rates` | 2 | Lists saved rates and which currencies are installed. |
| `/skysleylines rates preview` | 4 | Previews a currency rate migration for the issuing operator. |
| `/skysleylines rates apply <revision>` | 4 | Applies that operator’s matching preview and backs up the prior ledger. |
| `/skysleylines account recover <currency> <units> [x y z]` | 4 | Returns a positive whole-unit amount of an inactive currency to raw magic at one eligible nexus. |

The operator vision toggle ends on logout, respawn, or loss of permission. Timed effects from other mods can continue independently. Ley Meter use requires permission level 2.

## Add a named nexus

See [Worldgen and nexus pins](WORLDGEN_AND_PINS.md#pin-a-nexus-in-an-existing-world) for preview, staging, retirement, and recovery commands. Locate shows authored Y for a pin and natural ground for a seeded nexus.

## Balance a live world

Use **Mods → Sky’s Ley Lines → Config** in a private single-player session or edit the server TOML while the world is closed. Change `rechargeTicksOverride` to retune recharge for an existing world. A value of `72000` is three Minecraft days for a full refill. Set `nexusCapacityMultiplier` to tune how much raw-equivalent charge a nexus can hold. The default of `0.01` gives 70 raw capacity to a nexus that originally held 7,000. Reopen the world and check `effectiveRechargeTicks` and `nexusCapacityMultiplier` with the profile command.

Before changing currency rates, read the current account and run the rate migration preview. Changing a pack’s configured rate leaves existing balances at their saved rate until you apply a preview. If other transactions invalidate the preview, create a fresh one. See the [configuration guide](CONFIGURATION.md) for config paths, backups, and save versions.
