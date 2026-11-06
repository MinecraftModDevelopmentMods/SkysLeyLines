package zone.moddev.mc.skysleylines.server;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zone.moddev.mc.skysleylines.SkysLeyLines;

/** A tracked view of a drop. Neither the entity nor the spawn packet contains a spendable balance. */
public final class EnergyOrb extends Entity {
    private UUID drop;
    private long nextPickup;

    public EnergyOrb(EntityType<? extends EnergyOrb> type, Level level) { super(type, level); }

    EnergyOrb(ServerLevel level, UUID drop, Vec3 position) {
        this(SkysLeyLines.ENERGY_ORB.get(), level);
        this.drop = drop;
        setPos(position);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        var energy = LeyServerContext.playerEnergy(level.getServer());
        if (!energy.validProjection(drop, this)) { discard(); return; }
        if (level.getGameTime() < nextPickup) return;
        nextPickup = level.getGameTime() + 5;
        for (var player : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(1.0),
                player -> player.isAlive() && !player.isSpectator())) {
            energy.pickup(drop, player);
            if (isRemoved()) break;
        }
    }
}
