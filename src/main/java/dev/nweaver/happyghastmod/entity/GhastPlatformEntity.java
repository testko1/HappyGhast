package dev.nweaver.happyghastmod.entity;

import dev.nweaver.happyghastmod.init.EntityInit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public class GhastPlatformEntity extends Entity {

    public static final EntityDimensions FIXED_DIMENSIONS = EntityDimensions.scalable(4.0F, 0.5F); // подбери размер

    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID =
            SynchedEntityData.defineId(GhastPlatformEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public GhastPlatformEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    // используем EntityInit
    public GhastPlatformEntity(Level level, double x, double y, double z, HappyGhast owner) {
        this(EntityInit.GHAST_PLATFORM.get(), level); // используем entityinit
        this.setPos(x, y, z);
        this.setOwnerUUID(owner.getUUID());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(OWNER_UUID, Optional.empty());
    }

    @Override
    public void push(Entity pEntity) {
        // платформа никого не толкает
    }

    @Override
    public void remove(RemovalReason reason) {
        System.out.println("GhastPlatformEntity " + this.getUUID() + " is being removed by the engine Reason: " + reason);
        super.remove(reason);
    }

    @Override
    public void tick() {
        // Systemoutprintln("GhastPlatformEntity " + thisgetUUID() + " is ticking (TickCount: " + thistickCount + ")"); // раскомментируй для отладки "зомби"
        super.tick();
        // остальная логика tick (проверка владельца и тд)
        if (!this.level().isClientSide && getOwnerUUID().isPresent()) {
            Entity owner = null;
            if (this.level() instanceof ServerLevel serverLevel) {
                owner = serverLevel.getEntity(getOwnerUUID().get());
            }

            if (owner == null || !owner.isAlive()) {
                System.out.println("Owner Ghast not found or dead, removing platform " + this.getUUID());
                this.remove(RemovalReason.DISCARDED); // или kill()
                return;
            }
        }

        this.setDeltaMovement(Vec3.ZERO);
        if (this.tickCount > 1) {
            this.xo = this.getX();
            this.yo = this.getY();
            this.zo = this.getZ();
        }
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(OWNER_UUID);
    }

    public void setOwnerUUID(@Nullable UUID uuid) {
        this.entityData.set(OWNER_UUID, Optional.ofNullable(uuid));
    }


    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return !this.isRemoved(); }
    @Override protected void doWaterSplashEffect() { }
    @Override public boolean isPushedByFluid() { return false; }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        // убедимся что boundingbox правильно обновляется
        // getdimensions() вызывается внутри setboundingbox
        if (this.getType() != null) { // проверка на всякий случай
            AABB aabb = this.getType().getDimensions().makeBoundingBox(x, y, z);
            this.setBoundingBox(aabb);
        } else {
            // фоллбек если тип еще не установлен
            this.setBoundingBox(FIXED_DIMENSIONS.makeBoundingBox(x, y, z));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) {
            this.setOwnerUUID(compound.getUUID("Owner"));
        } else {
            this.setOwnerUUID(null); // убедимся что сбрасывается если нет в nbt
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        getOwnerUUID().ifPresent(uuid -> compound.putUUID("Owner", uuid));
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}