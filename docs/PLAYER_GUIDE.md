# Player guide

A ley line is a band of natural magic. A nexus forms where lines cross. The terrain and world seed determine where these sites are. The strength you can sense depends on your position, including how high or low you are relative to the natural ground.

## Find a line with dowsing rods

1. Craft Dowsing Rods with two copper ingots and two sticks in any arrangement. In Creative, find them under **Tools & Utilities**. The item ID is `skysleylines:dowsing_rods`.
2. Hold the item with your other hand empty to see a rod in each hand. The rods turn towards a nearby line and align along it when you reach it. They cross inside a usable nexus field.
3. Right-click for a coarse direction and strength reading. The reading can point to a nexus up to 512 blocks away or a line up to 256 blocks away. The rods also show an approximate reserve state while you stand inside a nexus field.

Try changing elevation if you reach a crossing and the rods stay quiet. The default field strength falls to half at 64 blocks above or below natural ground and reaches zero at 128 blocks. In some places the natural ground is the seabed. The current layout is active in the Overworld.

## See the field

Ley Vision shows nearby lines and crossings over terrain already loaded by your client. Four built-in styles are available.

| Style | Appearance |
| --- | --- |
| River | Flowing ribbons with branching lightning and violet nexus plumes |
| Crackle | Narrow traces and low arcs |
| Sparkles | Small motes over the terrain |
| Glow | A soft band and shallow nexus halo rendered as an overlay |

An operator can use `/skysleylines vision on`. An installed mod or a pack script can also grant timed vision. The overlay follows loaded terrain and water. Use the rods or a position reading to tell whether you can draw power at your current height.

## Use nexus power

A nexus has one shared pool. An add-on can convert raw magic into a named currency and let machines or spells spend that currency. Converted balances still occupy part of the same pool. Spending currency creates space for raw magic to recharge.

For example, a machine might convert 5 raw magic into 5,000 aether at a rate of 1,000 aether per raw. The conversion leaves the total charge unchanged. Spending 1,000 aether frees space for 1 raw magic to recharge. The world’s saved currency rate determines the actual amount.

To inspect exact amounts with a Ley Meter or an operator command, see the [server operator guide](SERVER_GUIDE.md).
