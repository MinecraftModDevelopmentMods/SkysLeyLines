# Pack maker guide

For NeoForge 1.21.1, ship `config/skysleylines-common.toml` for new-world layout, capacity, and base recharge. Ship `config/skysleylines-server.toml` for the default vision style and optional recharge override. A server operator can place a copy in `<world>/serverconfig/` to set those two values for one world. The Ley Meter and `/skysleylines profile` show the applied settings. See [configuration and save compatibility](CONFIGURATION.md).

From 0.5.5 you can configure player pools and react to balance and job events. See the [player energy guide](PLAYER_ENERGY.md) for examples and policy files.

## KubeJS

The optional scripting bridge was introduced in Sky’s Ley Lines 0.5.1. Install the matching [KubeJS build 381](https://www.curseforge.com/minecraft/mc-mods/kubejs/files/9396059/additional-files) stack for Minecraft 1.21.1. Put these scripts on the server, or in the instance for single-player packs.

Save this as `kubejs/startup_scripts/ley_currency.js` and restart the game or dedicated server.

```js
StartupEvents.init(() => {
  LeyLines.registerCurrency('kubejs:survey_aether', '1000')
})
```

Save this as `kubejs/server_scripts/ley_survey.js`. Aim into the air near a nexus and right-click with an amethyst shard in your main hand to convert raw magic. Right-click with a spyglass to spend the currency on a minute of Crackle vision.

```js
ItemEvents.rightClicked('minecraft:amethyst_shard', event => {
  if (event.hand.name() !== 'MAIN_HAND') return
  const player = event.player
  const pos = player.blockPosition()
  const result = LeyLines.convertAt(player.serverLevel(), pos.x, pos.y, pos.z,
    'kubejs:survey_aether', '5', 'kubejs:survey_converter', player)
  player.tell('Ley conversion: ' + result.get('status'))
})

ItemEvents.rightClicked('minecraft:spyglass', event => {
  if (event.hand.name() !== 'MAIN_HAND') return
  const player = event.player
  const pos = player.blockPosition()
  const result = LeyLines.spendAt(player.serverLevel(), pos.x, pos.y, pos.z,
    'kubejs:survey_aether', '1000', 'kubejs:survey_vision', player)
  if (result.get('succeeded')) {
    LeyLines.grantVision(player, 'kubejs:survey_vision', 20 * 60, 'skysleylines:crackle')
    event.cancel()
  } else {
    player.tell('Ley payment: ' + result.get('status'))
  }
})
```

| Scope | Method |
| --- | --- |
| Startup | `registerCurrency(id, unitsPerRaw)` |
| Server | `inspectAt(level, x, y, z)` |
| Server | `convertAt(level, x, y, z, currency, raw, consumer[, actor])` |
| Server | `spendAt(level, x, y, z, currency, units, consumer[, actor])` |
| Server | `returnToRawAt(level, x, y, z, currency, units, consumer[, actor])` |
| Server | `grantVision(player, source, durationTicks[, style])` |
| Server | `revokeVision(player, source)` |

Startup registration takes a unique `kubejs:` currency ID and a positive exact rate string. For a transaction, use a namespaced consumer ID and pass the responsible player when there is one. The script owns gameplay permissions and should apply rewards only after payment succeeds. Omit the vision style to use the server default. See the [public API reference](API.md#convert-spend-and-return) for transaction result codes.

Inspection returns a map with `active`, the field strengths, coordinates, and a nullable `account`. The account has `nexus`, `strength`, `rechargeTicks`, and `balance`. A balance has `raw`, `total`, `capacity`, `tick`, and a `currencies` map keyed by ID. Each currency row has `units`, `unitsPerRaw`, and `active`. Transactions return `succeeded` and `status`. Success adds `nexus`, `revision`, `before`, and `after`. Use map `get` calls to read fields. Balances, ticks and revisions that can exceed JavaScript integer precision are strings. Supply amounts as strings, for example `"1000"` or `"3/2"` for raw magic or rates.

Use `/reload` for server script changes. Restart the entire process for startup definitions. A world keeps its saved rates when a pack changes a definition. Use the [rate migration commands](CONFIGURATION.md) to apply changed rates. Removing a definition or the KubeJS mod marks the retained currency inactive until the same ID returns.

## Recipes and quests

The dowsing rod recipe ID and item ID are both `skysleylines:dowsing_rods`. To put rods behind a pack milestone, replace the standard recipe using one of these approaches.

```js
ServerEvents.recipes(event => {
  event.remove({ id: 'skysleylines:dowsing_rods' })
  event.shapeless('skysleylines:dowsing_rods', [
    'minecraft:copper_ingot', 'minecraft:copper_ingot',
    'minecraft:stick', 'minecraft:stick', 'minecraft:amethyst_shard'
  ]).id('kubejs:dowsing_rods')
})
```

```zenscript
craftingTable.removeByName(["skysleylines:dowsing_rods"]);
craftingTable.addShapeless("pack_dowsing_rods", <item:skysleylines:dowsing_rods>,
    [<item:minecraft:copper_ingot>, <item:minecraft:copper_ingot>,
     <item:minecraft:stick>, <item:minecraft:stick>, <item:minecraft:amethyst_shard>]);
```

For an FTB Quests discovery quest, use the rod item as an item task or reward. Pair the reward with a short instruction to leave the other hand empty and follow the turning rods. The Ley Meter is a separate operator tool.

[KubeJS recipe guide](https://kubejs.com/wiki/tutorials/recipes/) · [CraftTweaker crafting table API](https://docs.blamejared.com/1.21.1/en/vanilla/api/recipe/manager/CraftingTableRecipeManager/) · [FTB Quests documentation](https://docs.feed-the-beast.com/mod-docs/1.21.1/mods/suite/Quests/Developer/Quests/Rewards/Command_Reward/)
