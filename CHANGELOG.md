# Changelog

## 0.5.4.121012 beta

This beta brings together the work since the 0.4.1 alpha. I’ve added a few ways to see what’s going on in your world and given pack makers more control over how the magic works.

- Ley Vision has four styles. River is the original look. Crackle, Sparkles and Glow make it easier to survey an area with less on screen. Mods can register a style for their own vision effects.
- The new Ley Meter shows how strong the field is at a block and what’s in the nexus account if you’re in reach of one. Operators can also give coordinates to `/skysleylines account`.
- Open the mod’s **Config** screen to change new-world defaults. In single-player, the server settings let you choose the default vision style and change the recharge time for an existing world. Reopen the world to apply a recharge change.
- If you remove a mod or script that defined a currency, an operator can return that currency to the nexus as raw magic. This build also rejects transactions that would produce values too large to save.
- With KubeJS installed, packs can define currencies, read the field and nexus accounts, make transactions and grant timed Ley Vision. The mod JAR includes guides and examples for players, server operators, pack makers and Java modders.
- The in-game text follows your Minecraft language. This build ships 18 locale files covering English, German, Spanish, French, Italian, Japanese, Dutch, Brazilian Portuguese, Russian, and Simplified and Traditional Chinese.

Use Minecraft 1.21.1, NeoForge 21.1.247 or a newer 21.1 build, and Java 21. Update the server and clients together. If you change an existing world’s recharge time, the mod backs up the old ledger before it saves the new format.
