# Local development

Target: Minecraft 1.21.1, NeoForge 21.1.247, Java 21, NeoGradle 7.1.38, Gradle 9.2.1. Scaffold derived from official NeoForgeMDKs/MDK-1.21.1-NeoGradle commit `50b02952c596784d7c493866650021c1222846ca`; example gameplay classes were not copied. The official wrapper binaries are retained.

```powershell
$env:JAVA_HOME='C:\Users\johnb\.jdks\temurin-21.0.7+6'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME='D:\MinecraftMineralogy\.gradle-verify-cache'
.\gradlew.bat clean --no-daemon
.\gradlew.bat build javadoc verifyArtifacts eclipse verifyEclipseProductionClasspath runtimeProbeJar --no-daemon --max-workers=2
```

Run one Gradle process at a time against the shared cache. Import the inner `SkysLeyLines` directory as an existing Gradle project in Eclipse. Use NeoGradle's `client` and `server` Gradle run tasks; do not manufacture Forge-era launch files or use `genEclipseRuns`. The Eclipse production classpath uses processed resources and excludes tests, JUnit, and the runtime probe. Test editing/running remains available through Gradle.

`src/runtimeProbe` is a disposable validation consumer, not part of the mod jar or ordinary launch. Its separate jar goes only into explicitly labelled probe runs. Production acceptance must also launch the exact ordinary jar without that probe.

Validation covers deterministic boundaries/order, concurrent samplers, bounded caches/searches, saved profiles, shared currency competition, exact conversions/returns, cancelled/reentrant events, frozen rates, value-preserving migrations, unloaded recovery, fractional recharge, NBT round trips/rejection, resources, exact packaged runtimes, and visual rods feedback. Runtime receipts are separate from user gameplay acceptance. Keep failed receipts and rerun only after fixing their cause or stating the changed validation conditions.

## Packaged documentation

Edit the public Markdown guides in `docs/` and link new pages from [the documentation index](README.md). Use relative links between bundled pages so readers can extract the directory and browse it offline. The ordinary JAR packages the guides under `META-INF/skysleylines/docs/` and advertises the index in its manifest. `verifyArtifacts` checks the packaged paths and bytes against the source guides.

This development note is checkout documentation. Keep machine-specific build paths and validation notes here or in the repository rather than in the public bundle.

## Beta releases

CI validates the wrapper and builds the ordinary, sources, and Javadoc JARs on Java 21. It publishes a checksum manifest with the artifacts and the changelog. Download the bundle named for the commit you want to test and use the ordinary JAR for the client and dedicated server checks.

After merge, take the CI run ID from the MMD branch push and verify its ordinary JAR matches the tested SHA-256. Open **Deploy Sky’s Ley Lines beta** on that branch and enter the run ID and tested SHA-256. Leave `confirm_publication` off to verify the bundle without publishing. With confirmation on, the default target publishes to MMD Maven first and then CurseForge.

The Maven job publishes the prepared JAR, sources, and Javadocs. It uses `MAVEN_UPLOAD_URL`, `MAVEN_UPLOAD_USERNAME`, and `MAVEN_UPLOAD_PASSWORD`, then downloads the public files and compares them to the bundle. The Maven coordinate is `zone.moddev.mc.skysleylines:SkysLeyLines:<version>`.

The CurseForge job uses `CURSEFORGE_TOKEN` to upload the ordinary JAR to project 1783346 as a beta. Check the returned file ID and the public file listing separately. For a Maven backfill of a version already on CurseForge, select the `maven` target and check that the merged CI bundle matches the released JAR’s checksum.

Research references:

- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/gettingstarted/modfiles/
- https://docs.neoforged.net/docs/1.21.1/datastorage/saveddata/
- https://docs.neoforged.net/docs/1.21.1/items/
- https://docs.neoforged.net/docs/1.21.1/networking/payload/
- https://docs.neoforged.net/docs/1.21.1/advanced/extensibleenums/

The dowsing animation uses target-native client render events, a paired third-person arm pose, and a clientbound payload sampled every five server ticks for equipped players. It adds no world storage or API/schema/layout change. Verify ordinary packaged dedicated-server class loading separately from client rendering, then check a disposable client's quiet/line/nexus transitions, yaw changes, item switching, occupied offhand, left-hand preference, third person, and resource reload. Production launch inputs must remain free of probes/test libraries.

