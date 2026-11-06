# Sky's Ley Lines

I wanted magic mods to be able to share the same map. Sky’s Ley Lines gives the Overworld a network of ley lines and nexuses based on the world seed. Where the lines cross, a nexus holds a pool of magic. Other mods and modpacks can draw on that pool and decide what to do with it.

## Try it in a world

Craft a pair of dowsing rods with two copper ingots and two sticks. Hold the pair with your other hand empty and the rods will turn towards a nearby line. They cross when you reach a nexus field. Right-click for a rough bearing and strength reading.

If you’re an operator, run `/skysleylines locate nexus` and click the coordinates to copy them. Switch on Ley Vision with `/skysleylines vision on`. River is the default, with Crackle, Sparkles and Glow for a quieter view. Pick up a Ley Meter from **Operator Utilities** and right-click a block to see the local field and any nexus account you can reach from there.

Open **Mods → Sky’s Ley Lines → Config** to set up new-world defaults. In a local single-player world you can also choose the default vision style and adjust how much a nexus can hold and how long it takes to refill. Operators can place a named nexus with `/skysleylines nexus pin add`. It takes effect the next time the world opens. The [operator guide](docs/SERVER_GUIDE.md) covers commands and dedicated servers.

## Build on it

A nexus has one shared reserve. Mods can register their own currencies, convert raw magic into them, and spend those balances on their own effects. They can also give players personal pools and let them draw from a nexus over time. Pack makers can do the same with optional KubeJS scripts. Start with the [Java quick start](docs/INTEGRATION.md) or the [pack maker guide](docs/MODPACKS.md).

The [documentation index](docs/README.md) is also in the mod JAR at `META-INF/skysleylines/docs/README.md`.

## Install

This beta is for **Minecraft 1.21.1, NeoForge 21.1.247 or later in the 21.1 line, and Java 21**. Use the same build on the server and clients. See the [changelog](CHANGELOG.md) for what’s changed since the alpha.

If you’re working on the source, use the [Eclipse setup guide](ECLIPSE.md). The Windows development launcher uses a [guarded GLFW runtime](GLFW.md).
