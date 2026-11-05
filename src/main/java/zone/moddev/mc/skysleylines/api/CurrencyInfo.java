package zone.moddev.mc.skysleylines.api;

/** The saved units-per-raw rate and whether the currency is registered in this session. */
public record CurrencyInfo(MagicAmount unitsPerRaw, boolean active) {}
