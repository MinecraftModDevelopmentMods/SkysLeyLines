# Sky’s Ley Lines 0.5.1.121012 beta

Minecraft 1.21.1, NeoForge 21.1.247 or later in the 21.1 line, Java 21. Install the matching build on client and server.

- Added four Ley Vision styles, an operator Ley Meter, and optional coordinates for account inspection since the 0.4.1 alpha.
- Added a per-world recharge override and an operator command to recover inactive currencies as raw magic.
- Added optional KubeJS support for pack-defined currencies, field and account readings, currency transactions and timed Ley Vision.
- Rejects currency rates and transaction results that cannot be encoded in the ledger save format.

Existing saves keep the same ley layout, nexus identities and currency balances. Changing the effective recharge rate creates a backup and writes schema 3. Continue to use this build or newer for any world that has written schema 3.

To try the beta, locate a nexus with `/skysleylines locate nexus`, enable `/skysleylines vision on`, then inspect the field and shared account with the Ley Meter. For pack setup and scripts, see the [pack maker guide](MODPACKS.md).
