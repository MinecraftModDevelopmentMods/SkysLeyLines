# Sky's Ley Lines

A small magical foundation for Minecraft 1.21.1 / NeoForge 21.1.247, by SkyBlade1978.

Ley lines and nexuses are calculated rather than built out of world blocks. Other mods register paradigms and energy currencies, use the same natural magical landscape, and share persistent nexus energy accounts. This initial alpha enables the Overworld only.

The only gameplay item is a pair of dowsing rods: combine two copper ingots and two sticks in any crafting grid. Equip the pair in either hand with the other hand empty: one rod appears in each hand, turns towards a nearby ley line, and smoothly crosses with its partner inside a nexus field. On a line the rods follow its direction; without a signal they settle straight ahead. This works automatically, including in third-person view and for left-handed players. Holding another tool uses a single rod in the available hand. Right-click still gives a direction and rough strength reading. The rods neither consume power nor reveal exact coordinates. They appear in Tools & Utilities in Creative.

Run `/skysleylines locate nexus` as an operator to find the closest Overworld nexus within up to 4,096 blocks. The result shows its coordinates, horizontal distance, and strength. Click the coordinates in chat to copy them. The reported Y is the natural ground height, which can be below water. Use `/skysleylines vision on` as you approach the site to see the crossing. The existing `/skysleylines nearest` command works the same way.

Operators can also use `/skysleylines sample [x y z]`, `/skysleylines profile`, `/skysleylines account`, and `/skysleylines rates` for precise diagnostics. These commands do not consume power or load chunks.

Use `/skysleylines vision` to toggle ley vision for yourself, or add `on` or `off` to set it directly. This requires operator permission level 2. In the Overworld, nearby ley lines appear as flowing cyan rivers with branching lightning. Violet plumes and orbiting rings mark nexuses. Fly above the terrain to survey routes and crossings before placing spawn points. Vision follows the terrain and water surface in loaded chunks and fades into the distance.

Default settings aim for strategic nexus sites roughly a kilometre or two apart. Strength follows natural terrain, falling to half at 64 blocks above/below it and zero at 128 blocks. Raw magic can be converted into child-registered currencies only in a nexus field. Conversion preserves one shared capacity; spending currency frees space for raw recharge. A fully drained nexus recovers over 24,000 server ticks, including while its chunks are unloaded, but not while the server is offline.

See [the API](docs/API.md), [configuration and save compatibility](docs/CONFIGURATION.md), and [development and validation](docs/DEVELOPMENT.md).

For Eclipse, follow [the Eclipse setup guide](ECLIPSE.md). Import the inner `SkysLeyLines` folder as an existing Gradle project, refresh Gradle, and launch through `runClient` or `runServer`.

Windows client launches in this project use a guarded GLFW runtime to prevent a native crash when attached input queues expose another Minecraft process's window. See [GLFW.md](GLFW.md) for its source, validation and standalone-launcher setup.

This mod does not require OreSpawn. OreSpawn integration and actual magical ore rules are future work. It adds no blocks, worldgen features, spells, player mana pools, claim system, or portals.

Local development candidate only. Automated verification and user gameplay acceptance are separate.

