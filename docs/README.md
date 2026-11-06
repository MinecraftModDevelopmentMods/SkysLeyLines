# Sky’s Ley Lines documentation

Sky’s Ley Lines adds a seed-derived magical field to the Overworld. Players can find it with dowsing rods. Mods and packs can build on the field, share a nexus’s reserve, and give players Ley Vision.

Start with the guide for what you want to do.

| I want to… | Guide |
| --- | --- |
| Find a ley line or nexus and understand the readings | [Player guide](PLAYER_GUIDE.md) |
| Install the mod, run operator commands, or check a nexus account | [Server operator guide](SERVER_GUIDE.md) |
| Change layout defaults, recharge, or Ley Vision style | [Configuration and save compatibility](CONFIGURATION.md) |
| Register pack currencies or write KubeJS scripts | [Pack maker guide](MODPACKS.md) |
| Build a Java mod that samples the field or uses nexus power | [Java integration quick start](INTEGRATION.md) and [public API reference](API.md) |
| Place structures at nexuses, sample ore candidates, or stage an admin pin | [Worldgen and nexus pins](WORLDGEN_AND_PINS.md) |
| Set up personal pools, timed transfers, death drops, or KubeJS events | [Player energy pools](PLAYER_ENERGY.md) |
| Diagnose a missing reading, payment failure, or startup problem | [Troubleshooting](TROUBLESHOOTING.md) |
| Check version requirements and what changed | [Current release and supported languages](RELEASE-0.5.4.md), [0.5.3](RELEASE-0.5.3.md), [0.5.2](RELEASE-0.5.2.md), and [0.5.1](RELEASE-0.5.1.md) |

## Finding this guide in a JAR

Open the ordinary mod JAR as a ZIP archive and look for `META-INF/skysleylines/docs/README.md`. The other guides are in the same directory. Extract that directory to follow the relative links offline. The JAR manifest also points to this page through the `SkysLeyLines-Documentation` attribute.

The public Java package is `zone.moddev.mc.skysleylines.api`. The manifest identifies the implementation version, Java API, layout version, and latest save schema that the build can read. Use the release guide for the matching Minecraft, NeoForge, Java, and KubeJS versions.
