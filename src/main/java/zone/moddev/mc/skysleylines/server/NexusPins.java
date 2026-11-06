package zone.moddev.mc.skysleylines.server;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import zone.moddev.mc.skysleylines.api.Nexus;
import zone.moddev.mc.skysleylines.api.NexusId;

final class NexusPins {
    static final String DIMENSION = "minecraft:overworld";
    static final int LIMIT = 256;
    enum State { STAGED_ADD, ACTIVE, STAGED_REMOVE, RETIRED }

    record Pin(ResourceLocation id, BlockPos position, double strength, State state) {
        Pin {
            if (id.getNamespace().isEmpty() || id.getPath().isEmpty() || id.toString().length() > 128
                    || Math.abs((long) position.getX()) >= 30_000_000
                    || Math.abs((long) position.getZ()) >= 30_000_000 || Math.abs((long) position.getY()) > 4096
                    || !(strength > 0 && strength <= 1)) throw new IllegalArgumentException("Invalid nexus pin");
            position = position.immutable();
        }

        Nexus nexus() {
            return new Nexus(NexusId.pinned(DIMENSION, id), position.getX(), position.getZ(), strength,
                    OptionalInt.of(position.getY()));
        }

        Pin state(State next) { return new Pin(id, position, strength, next); }
        boolean occupiesAnchor() { return state != State.RETIRED; }
    }

    private final Map<ResourceLocation, Pin> entries = new TreeMap<>();

    Pin get(ResourceLocation id) { return entries.get(id); }
    List<Pin> list() { return List.copyOf(entries.values()); }

    List<Nexus> active() {
        return entries.values().stream().filter(pin -> pin.state == State.ACTIVE || pin.state == State.STAGED_REMOVE)
                .map(Pin::nexus).toList();
    }

    void checkAdd(Pin pin) {
        if (entries.containsKey(pin.id)) throw new IllegalArgumentException("Pin ID is already reserved: " + pin.id);
        if (entries.values().stream().filter(Pin::occupiesAnchor).count() >= LIMIT)
            throw new IllegalArgumentException("This world already has " + LIMIT + " active or staged pins");
        if (entries.values().stream().anyMatch(other -> other.occupiesAnchor() && other.position.equals(pin.position)))
            throw new IllegalArgumentException("A pin already occupies that block");
    }

    void put(Pin pin) { entries.put(pin.id, pin); }

    boolean activateStaged() {
        boolean changed = false;
        for (var entry : entries.entrySet()) {
            var pin = entry.getValue();
            State next = switch (pin.state) {
                case STAGED_ADD -> State.ACTIVE;
                case STAGED_REMOVE -> State.RETIRED;
                default -> pin.state;
            };
            if (next != pin.state) {
                entry.setValue(pin.state(next));
                changed = true;
            }
        }
        return changed;
    }

    void read(CompoundTag root) {
        for (Tag entry : LeyWorldData.compounds(root, "pins")) {
            var row = (CompoundTag) entry;
            LeyWorldData.require(row, "id", Tag.TAG_STRING);
            LeyWorldData.require(row, "state", Tag.TAG_STRING);
            LeyWorldData.require(row, "strength", Tag.TAG_DOUBLE);
            for (String axis : List.of("x", "y", "z")) LeyWorldData.require(row, axis, Tag.TAG_INT);
            var pin = new Pin(LeyWorldData.identifier(row.getString("id")),
                    new BlockPos(row.getInt("x"), row.getInt("y"), row.getInt("z")),
                    row.getDouble("strength"), State.valueOf(row.getString("state")));
            if (pin.occupiesAnchor()) checkAdd(pin);
            if (entries.putIfAbsent(pin.id, pin) != null) throw new IllegalArgumentException("Duplicate pin: " + pin.id);
        }
    }

    ListTag write() {
        var rows = new ListTag();
        entries.values().forEach(pin -> {
            var row = new CompoundTag();
            row.putString("id", pin.id.toString());
            row.putString("state", pin.state.name());
            row.putInt("x", pin.position.getX());
            row.putInt("y", pin.position.getY());
            row.putInt("z", pin.position.getZ());
            row.putDouble("strength", pin.strength);
            rows.add(row);
        });
        return rows;
    }
}
