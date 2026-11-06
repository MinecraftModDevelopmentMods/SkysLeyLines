# Changelog

## 0.5.6.121012 beta

I’ve brought the guides in the JAR up to date with player pools, timed transfers, nexus pins, and the smaller nexus reserves. The Java quick start now shows how to get Ley Lines from MMD Maven, and the KubeJS guides cover player pools and job events.

- KubeJS `LeyLines.inspectAt` now gives you the nexus centre. If the nexus is a named pin, you also get its ID and authored height, so a pack can recognise a site even when the player is some distance from its anchor.
- The JAR manifest now reports save schema 6, which 0.5.5 already used for nexus capacity changes. Worlds keep their saved layout, accounts, and player pools.

## 0.5.5.121012 beta

I wanted magic mods to be able to share a nexus even when they use completely different kinds of magic. A technomancer can now carry aether and a mage can carry mana. Each mod decides who gets a pool and how big it is, and both can draw from the same nexus.

- Transfers and conversions can happen instantly or over time. Mods can also let players refill from a nearby nexus at a rate they choose.
- Player energy drops on death by default. Pack makers can change that behavior, tune capacities and rates, and restrict transfers between players.
- KubeJS can read and change pools, start and cancel jobs, and react when a pool runs empty.
- Structure mods can ask for all the nexuses in a chunk from a generation worker. Ore placement can reuse the column sampler.
- Operators can stage named nexuses and their connections for the next world start. A retired pin keeps its converted balances available for recovery at another nexus.
- Nexus pools now default to 1% of their original size. A 7,000-raw nexus holds 70 raw, enough for 700 units at a rate of 10 units per raw. Change **Nexus Capacity Multiplier** in Server Settings and reopen the world to adjust it. Existing converted energy stays available.
- The ledger backs itself up before changing the pool multiplier or first adding player-pool or pin data. Multiplier changes use save schema 6. Use the same build on the client and server.

This beta is for Minecraft 1.21.1 on NeoForge 21.1.247 or later in the 21.1 line, with Java 21.

## 0.5.4.121012 beta

This beta brings together the work since the 0.4.1 alpha. I’ve added a few ways to see what’s going on in your world and given pack makers more control over how the magic works.

- Ley Vision has four styles. River is the original look. Crackle, Sparkles and Glow make it easier to survey an area with less on screen. Mods can register a style for their own vision effects.
- The new Ley Meter shows how strong the field is at a block and what’s in the nexus account if you’re in reach of one. Operators can also give coordinates to `/skysleylines account`.
- Open the mod’s **Config** screen to change new-world defaults. In single-player, the server settings let you choose the default vision style and change the recharge time for an existing world. Reopen the world to apply a recharge change.
- If you remove a mod or script that defined a currency, an operator can return that currency to the nexus as raw magic. This build also rejects transactions that would produce values too large to save.
- With KubeJS installed, packs can define currencies, read the field and nexus accounts, make transactions and grant timed Ley Vision. The mod JAR includes guides and examples for players, server operators, pack makers and Java modders.
- The in-game text follows your Minecraft language. This build ships 18 locale files covering English, German, Spanish, French, Italian, Japanese, Dutch, Brazilian Portuguese, Russian, and Simplified and Traditional Chinese.

Use Minecraft 1.21.1, NeoForge 21.1.247 or a newer 21.1 build, and Java 21. Update the server and clients together. If you change an existing world’s recharge time, the mod backs up the old ledger before it saves the new format.
