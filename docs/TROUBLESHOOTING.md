# Troubleshooting

| Symptom | What to check |
| --- | --- |
| Minecraft reports a missing mod or version mismatch | Use Minecraft 1.21.1, a supported NeoForge 21.1 build, Java 21, and the matching Sky’s Ley Lines JAR on the client and server. Remove duplicate copies from that instance’s mod list. |
| The rods stay quiet or the meter reports an inactive field | Check that you are in the Overworld and above a column with natural ground. If you are far above or below ground, try moving closer to the natural ground height. |
| A line is visible but there is no eligible nexus | Move towards a crossing and use `/skysleylines sample` at the machine’s exact height. A line contributes field strength. Spending and conversion require an eligible nexus. |
| A converter runs out of raw magic while the account looks full | Check the currency balances. Converted value occupies the same capacity. Spending a currency frees space for raw magic to recharge. An inactive balance can be recovered by an operator. |
| The Config screen shows a disabled Server Settings button | Open a private single-player world to edit the server settings in the GUI. For a dedicated server or LAN host, edit the server TOML with the world closed. |
| A recharge edit appears to have no effect | Check the exact setting name. `rechargeTicks` supplies a new-world default. `rechargeTicksOverride` applies to an existing world after restart. Confirm the effective value with `/skysleylines profile`. |
| A changed currency rate still shows the old number | The account displays the saved world rate. Restart after editing the currency definition, then use the operator rate preview and apply commands to migrate. |
| A currency shows as inactive | Restore the mod or startup script that registered the same currency ID, or use the operator recovery command to return specific units to raw. |
| A script’s payment fails or a precise balance looks wrong | Supply exact amounts as strings, pass the right level and block coordinates, and check the status before applying the gameplay effect. JavaScript numbers can round large balances. |
| The server refuses to load ley data | Check the startup log for the save schema, config key, or invalid field it reports. Keep the original ledger and backups. Use a build that supports the schema and check [save compatibility](CONFIGURATION.md) before restoring a backup. |

For a Windows startup crash in `glfw.dll`, include the native crash report and launcher details. The project’s guarded GLFW runtime is a separate launcher setup. The mod JAR loads after the early native window is created.

When reporting an issue, include the exact versions, whether you were in single-player or on a dedicated server, and the relevant log excerpt or command output. For a field or balance issue, include the dimension, coordinates, `profile`, `sample`, and `account` readings if you can share them.

[Release requirements](RELEASE-0.5.6.md) · [Operator commands](SERVER_GUIDE.md) · [Pack scripting](MODPACKS.md)
