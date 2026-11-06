# Sky’s Ley Lines 0.5.6.121012 beta

I’ve updated the guides that ship inside the JAR so pack makers and modders can find the player energy, worldgen, and nexus pin features in one place. KubeJS can now identify a named nexus from an account reading, including its authored height. The JAR manifest reports schema 6, the save format already supported in 0.5.5.

## Install or upgrade

- Use Minecraft 1.21.1, NeoForge 21.1.247 or later in the 21.1 line, and Java 21. Install the same Ley Lines build on the server and clients.
- The optional KubeJS stack uses [KubeJS NeoForge 2101.7.2-build.381](https://www.curseforge.com/minecraft/mc-mods/kubejs/files/9396059) and [Rhino 2101.2.7-build.85](https://modrinth.com/mod/rhino/version/cQ4POTah). See the [pack maker guide](MODPACKS.md) for startup registration and server scripts.
- Back up your world before upgrading. The seeded layout is version 1, the Java API is version 2, and the network protocol is version 4. This beta reads the same world ledger schemas as 0.5.5. See [configuration and save compatibility](CONFIGURATION.md) before reopening a world from an older beta.

## If you’re coming from 0.5.4

- Mods can give each player personal pools of converted currency and move energy [instantly or over time](PLAYER_ENERGY.md). Pack makers can configure pools and jobs with KubeJS, including what happens to player energy on death.
- Structure mods can [query nexuses in a chunk](WORLDGEN_AND_PINS.md) during world generation. Operators can stage named nexuses and retire or recover old pins.
- Nexus capacity defaults to 1% of its base value. Use the [server capacity multiplier](CONFIGURATION.md) to change it and reopen the world.

See the [documentation index](README.md) and the [list of supported languages](RELEASE-0.5.4.md).
