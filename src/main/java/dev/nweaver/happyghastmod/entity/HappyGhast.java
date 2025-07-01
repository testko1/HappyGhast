package dev.nweaver.happyghastmod.entity;

import dev.nweaver.happyghastmod.api.IQuadLeashTarget;
import dev.nweaver.happyghastmod.entity.components.*;
import dev.nweaver.happyghastmod.entity.goals.HappyGhastFollowPlayerWithItemGoal;
import dev.nweaver.happyghastmod.entity.goals.HappyGhastLookGoal;
import dev.nweaver.happyghastmod.entity.goals.HappyGhastRandomFloatGoal;
import dev.nweaver.happyghastmod.init.SoundInit;
import dev.nweaver.happyghastmod.leash.MultiLeashData;
import dev.nweaver.happyghastmod.mixin.MobAccessor;
import dev.nweaver.happyghastmod.network.GhastQuadLeashSyncPacket;
import dev.nweaver.happyghastmod.network.GhastRotationSyncPacket;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.PacketDistributor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class HappyGhast extends Ghast {


    // синхронизированные данные
    private static final EntityDataAccessor<Boolean> DATA_SADDLE_ID = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DESCENDING_ID = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ASCENDING_ID = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DATA_HARNESS_COLOR = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_SPEED_MULTIPLIER = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_HAS_LEASHED_BOAT = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    public static final EntityDataAccessor<CompoundTag> DATA_QUAD_LEASHED_ENTITIES = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.COMPOUND_TAG);
    // квадро-привязь
    public static final EntityDataAccessor<Boolean> DATA_IS_QUAD_LEASHING = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> DATA_QUAD_LEASHED_ENTITY_UUID = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.OPTIONAL_UUID);
    private float lastSyncedYRot;
    private float lastSyncedXRot;
    private static final float ROTATION_SYNC_THRESHOLD = 3F; // порог в градусах для отправки пакета
    private static final int ROTATION_SYNC_INTERVAL = 3; // интервал между отправками
    private int rotationSyncCooldown = 0; // кулдаун для отправки пакетов
    // макс кол-во привязанных сущностей
    private static final int MAX_QUAD_LEASHED_ENTITIES = 4;
    // компоненты
    private GhastDataComponent dataComponent;
    private GhastLeashComponent leashComponent;
    private GhastPlatformComponent platformComponent;
    private GhastRidingComponent ridingComponent;
    private GhastInteractionComponent interactionComponent;
    private GhastMovementComponent movementComponent;
    private GhastInventoryComponent inventoryComponent;

    // начальная высота спавна
    private double spawnHeight;
    // диапазон вертикального перемещения
    // флаг инициализации высоты спавна
    private boolean hasInitializedSpawnHeight = false;

    public HappyGhast(EntityType<? extends Ghast> type, Level level) {
        super(type, level);
        this.xpReward = 5;

        // инициализация компонентов в отдельном методе
        initComponents();
        // ставим множитель скорости при создании
        if (!this.level().isClientSide) {
            this.setSpeedMultiplier(GhastMovementComponent.SPEED_MULTIPLIER);
        }
    }

    // переменные для серверной ротации
    private float targetServerYRot;
    private float targetServerXRot;
    private float targetServerYBodyRot;
    private float targetServerYHeadRot;
    private boolean hasTargetRotation = false;
    private int rotationSyncCounter = 0;

    // реген
    private static final int CLOUD_LEVEL = 195;
    private static final int REGEN_INTERVAL = 50; // тики между регенерацией (2.5 сек)
    private int regenCounter = 0;


    // инициализация компонентов
    private void initComponents() {
        if (this.dataComponent == null) {
            this.dataComponent = new GhastDataComponent(this);
        }

        if (this.platformComponent == null) {
            this.platformComponent = new GhastPlatformComponent(this);
        }

        if (this.leashComponent == null) {
            this.leashComponent = new GhastLeashComponent(this);
        }

        if (this.ridingComponent == null) {
            this.ridingComponent = new GhastRidingComponent(this, dataComponent, platformComponent);
        }

        if (this.movementComponent == null) {
            this.movementComponent = new GhastMovementComponent(this, dataComponent, platformComponent, ridingComponent);
        }

        if (this.interactionComponent == null) {
            this.interactionComponent = new GhastInteractionComponent(this, dataComponent, leashComponent, platformComponent);
        }

        if (this.inventoryComponent == null) {
            this.inventoryComponent = new GhastInventoryComponent(this, dataComponent);
        }

    }

// геттеры/сеттеры для entityData

    // доступ к entityData
    public <T> T getEntityDataValue(EntityDataAccessor<T> key) {
        return this.entityData.get(key);
    }

    public <T> void setEntityDataValue(EntityDataAccessor<T> key, T value) {
        this.entityData.set(key, value);
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false; // не исчезает в мирном
    }

    public int getMaxPassengers() {
        return 4;
    }
    public List<UUID> getQuadLeashedEntityUUIDs() {
        CompoundTag tag = this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITIES);
        List<UUID> result = new ArrayList<>();

        if (tag == null) {
            // создаем пустой тег, если его нет
            tag = new CompoundTag();
            this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, tag);
            return result;
        }

        // обратная совместимость с одиночной привязью
        Optional<UUID> legacyTargetUUID = this.getQuadLeashedEntityUUID();
        if (this.isQuadLeashing() && legacyTargetUUID.isPresent()) {
            UUID legacyUUID = legacyTargetUUID.get();

            // добавляем, если еще не в списке
            boolean alreadyIncluded = false;
            for (int i = 0; i < MAX_QUAD_LEASHED_ENTITIES; i++) {
                String key = "entity" + i;
                if (tag.hasUUID(key) && tag.getUUID(key).equals(legacyUUID)) {
                    alreadyIncluded = true;
                    break;
                }
            }

            if (!alreadyIncluded) {
                result.add(legacyUUID);

                // миграция legacy uuid в новую структуру
                if (!this.level().isClientSide) {
                    for (int i = 0; i < MAX_QUAD_LEASHED_ENTITIES; i++) {
                        String key = "entity" + i;
                        if (!tag.hasUUID(key)) {
                            tag.putUUID(key, legacyUUID);
                            this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, tag);

                            // очищаем legacy данные
                            this.setQuadLeashedEntityUUID(Optional.empty());
                            break;
                        }
                    }
                }
            }
        }

        // читаем список из тега
        for (int i = 0; i < MAX_QUAD_LEASHED_ENTITIES; i++) {
            String key = "entity" + i;
            if (tag.hasUUID(key)) {
                UUID uuid = tag.getUUID(key);
                // защита от пустого uuid
                if (uuid != null && !uuid.equals(new UUID(0, 0))) {
                    result.add(uuid);
                }
            }
        }

        // отладка для сервера
        if (!this.level().isClientSide && this.tickCount % 100 == 0 && this.isQuadLeashing()) {
            //LOGGER.debug("Found {} quad leashed entities for Ghast {}", result.size(), this.getUUID())
        }

        return result;
    }


    public int getQuadLeashedEntityCount() {
        return getQuadLeashedEntityUUIDs().size();
    }

    public GhastInventoryComponent getInventoryComponent() {
        if (this.inventoryComponent == null) {
            if (this.dataComponent == null) {
                initComponents();
            }
            this.inventoryComponent = new GhastInventoryComponent(this, dataComponent);
        }
        return this.inventoryComponent;
    }

    @Override
    public void setYHeadRot(float yHeadRot) {
        super.setYHeadRot(yHeadRot);

        // синхронизация поворота головы
        if (!this.level().isClientSide && this.isVehicle()) {
        }
    }
    @Override
    public void setYBodyRot(float yBodyRot) {
        super.setYBodyRot(yBodyRot);

        // синхронизация поворота тела
        if (!this.level().isClientSide && this.isVehicle()) {
        }
    }


    @Override
    public void setYRot(float yRot) {
        super.setYRot(yRot);

        // проверяем на сервере, изменился ли поворот
        if (!this.level().isClientSide && this.isVehicle()) {

            if (Math.abs(this.getYRot() - lastSyncedYRot) > ROTATION_SYNC_THRESHOLD) {
                syncRotationToClients();
            }
        }
    }

    @Override
    public void setXRot(float xRot) {
        super.setXRot(xRot);

        // проверяем на сервере, изменился ли поворот
        if (!this.level().isClientSide && this.isVehicle()) {

            if (Math.abs(this.getXRot() - lastSyncedXRot) > ROTATION_SYNC_THRESHOLD) {
                syncRotationToClients();
            }
        }
    }

    // улучшенный метод синхронизации ротации
    private void syncRotationToClients() {
        if (this.level().isClientSide) return;

        // проверяем кулдаун
        if (rotationSyncCooldown > 0) {
            rotationSyncCooldown--;
            return;
        }

        // нормализуем разницу угла
        float yRotDiff = Math.abs(this.getYRot() - lastSyncedYRot);
        if (yRotDiff > 180.0F) yRotDiff = 360.0F - yRotDiff;
        float xRotDiff = Math.abs(this.getXRot() - lastSyncedXRot);

        // проверяем, если изменение достаточно большое
        if (yRotDiff < ROTATION_SYNC_THRESHOLD && xRotDiff < ROTATION_SYNC_THRESHOLD) {
            return;
        }

        // обновляем последние синхронизированные значения
        lastSyncedYRot = this.getYRot();
        lastSyncedXRot = this.getXRot();

        // создаем и отправляем пакет
        GhastRotationSyncPacket packet = new GhastRotationSyncPacket(
                this.getId(),
                this.getYRot(),
                this.getXRot(),
                this.yBodyRot,
                this.yHeadRot
        );

        // ставим кулдаун
        rotationSyncCooldown = ROTATION_SYNC_INTERVAL;

        // отправляем пакет всем, кроме водителя
        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> this),
                packet
        );
    }

    public void setTargetServerRotation(float yRot, float xRot, float yBodyRot, float yHeadRot) {
        this.targetServerYRot = yRot;
        this.targetServerXRot = xRot;
        this.targetServerYBodyRot = yBodyRot;
        this.targetServerYHeadRot = yHeadRot;
        this.hasTargetRotation = true;
    }

    public GhastPlatformComponent getPlatformComponent() {
        // можно добавить проверку на null, если компонент может быть null
        if (this.platformComponent == null && !this.level().isClientSide()) {
            return this.platformComponent;
        }
        return this.platformComponent;
    }

    // Синхронизированные данные
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();

        this.entityData.define(DATA_SADDLE_ID, false);
        this.entityData.define(DATA_ASCENDING_ID, false);
        this.entityData.define(DATA_DESCENDING_ID, false);
        this.entityData.define(DATA_HARNESS_COLOR, "blue"); // стандартный цвет - синий
        this.entityData.define(DATA_SPEED_MULTIPLIER, 1.0F);
        this.entityData.define(DATA_IS_QUAD_LEASHING, false);
        this.entityData.define(DATA_QUAD_LEASHED_ENTITY_UUID, Optional.empty());
        this.entityData.define(DATA_QUAD_LEASHED_ENTITIES, new CompoundTag());
    }

// делегирующие методы для доступа к данным

    public boolean isSaddled() {

        return this.getEntityDataValue(DATA_SADDLE_ID);

    }


    private static final Logger LOGGER = LogManager.getLogger(HappyGhast.class);
    public String getHarnessColor() {
        return this.getEntityDataValue(DATA_HARNESS_COLOR);
    }

    public void setHarnessColor(String color) {
        this.setEntityDataValue(DATA_HARNESS_COLOR, color);
    }

    public void setSaddled(boolean saddled) {
        this.setEntityDataValue(DATA_SADDLE_ID, saddled);
    }

    public boolean isAscending() {
        return this.getEntityDataValue(DATA_ASCENDING_ID);
    }

    public void setAscending(boolean ascending) {
        this.setEntityDataValue(DATA_ASCENDING_ID, ascending);
    }

    public boolean isDescending() {
        return this.getEntityDataValue(DATA_DESCENDING_ID);
    }

    public void setDescending(boolean descending) {
        this.setEntityDataValue(DATA_DESCENDING_ID, descending);
    }

    public boolean isQuadLeashing() {
        if (this.entityData == null) return false; // защита от NPE при инициализации
        try { return this.getEntityDataValue(DATA_IS_QUAD_LEASHING); }
        catch (Exception e) { LOGGER.error("Error accessing DATA_IS_QUAD_LEASHING", e); return false; }
    }
    public void setQuadLeashing(boolean quadLeashing) { this.setEntityDataValue(DATA_IS_QUAD_LEASHING, quadLeashing); }
    public Optional<UUID> getQuadLeashedEntityUUID() {
        if (this.entityData == null) return Optional.empty();
        try { return this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITY_UUID); }
        catch (Exception e) { LOGGER.error("Error accessing DATA_QUAD_LEASHED_ENTITY_UUID", e); return Optional.empty(); }
    }
    public void setQuadLeashedEntityUUID(Optional<UUID> uuid) { this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITY_UUID, uuid); }

    // Взаимодействие
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // обновляем якорь при взаимодействии с кулдауном
        AnchorManager.setAnchorWithCooldown(this, this.getX(), this.getY(), this.getZ());

        // проверка на shift+пкм для стандартного поводка
        // если игрок с шифтом и пустой рукой кликает на привязанного гаста (не квадро)
        if (player.isSecondaryUseActive() && player.getItemInHand(hand).isEmpty() &&
                this.getLeashHolder() != null && !this.isQuadLeashing()) {
            if (!this.level().isClientSide) {
                // отвязываем и даем поводок игроку
                this.dropLeash(true, false); // отвязываем без дропа
                player.getInventory().add(new ItemStack(Items.LEAD)); // даем предмет игроку

                // звук
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        net.minecraft.sounds.SoundEvents.LEASH_KNOT_BREAK,
                        net.minecraft.sounds.SoundSource.NEUTRAL, 0.5F, 0.8F);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // стандартное взаимодействие через компонент
        if (interactionComponent != null) {
            return interactionComponent.handleInteraction(player, hand);
        }
        return super.mobInteract(player, hand);
    }

    // Езда верхом
    @Override
    public boolean canAddPassenger(Entity passenger) {
        return ridingComponent != null && ridingComponent.canAddPassenger(passenger);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (ridingComponent != null) {
            ridingComponent.handlePassengerRemoval(passenger);
        }
    }

    @Override
    public LivingEntity getControllingPassenger() {
        return ridingComponent != null ? ridingComponent.getControllingPassenger() : null;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (ridingComponent != null) {
            ridingComponent.positionRider(passenger, moveFunction);
        } else {
            super.positionRider(passenger, moveFunction);
        }
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        super.onPassengerTurned(passenger);

        // если водитель повернулся, инициируем синхронизацию
        if (!this.level().isClientSide &&
                passenger == this.getControllingPassenger()) {
        }
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return ridingComponent != null ?
                ridingComponent.getDismountLocationForPassenger(passenger) :
                super.getDismountLocationForPassenger(passenger);
    }

    // Движение и физика
    @Override
    public void travel(Vec3 travelVector) {
        if (movementComponent != null) {
            movementComponent.handleTravel(travelVector);

            // если гаст управляется игроком, не вызываем стандартное поведение
            if (this.isVehicle() || !this.isAlive() || (this.platformComponent != null && this.platformComponent.isActive() && !this.level().isClientSide)) {
                return;
            }
        }
        super.travel(travelVector);
    }

    // вспомогательный метод для компонента движения
    public void applyControlledMovement(Vec3 movement) {
        super.travel(movement);
    }

    @Override
    public void push(Entity entity) {
        if (platformComponent == null || !platformComponent.shouldDisableCollisionWith(entity)) {
            super.push(entity);
        }
    }

    @Override
    public void push(double dx, double dy, double dz) {
        if (level().isClientSide || platformComponent == null || !platformComponent.isActive()) {
            super.push(dx, dy, dz);
        }
    }

    @Override
    public boolean isPushable() {
        return movementComponent != null ? movementComponent.isPushable() : super.isPushable();
    }

    @Override
    public boolean canCollideWith(Entity otherEntity) {
        return (movementComponent != null ? movementComponent.canCollideWith(otherEntity) : true)
                && super.canCollideWith(otherEntity);
    }

    // Основная логика сущности
    private long lastLoggedTick = 0;
    private static final int LOG_INTERVAL = 50; // интервал в тиках


    private void serverTick() {
        tickQuadLeash();

        if (dataComponent == null) {
            initComponents();
            if (this.tickCount - lastLoggedTick >= LOG_INTERVAL) {
                lastLoggedTick = this.tickCount;
            }
        }

        // проверяем, привязан ли гаст
        boolean isLeashedToSomething = this.isLeashed() && !this.isQuadLeashing();

        // если привязан, отключаем якоря
        if (isLeashedToSomething) {
            // отключаем следование
            AnchorManager.stopFollowing(this);

            // чистим якорь, чтобы не мешал поводку
            if (AnchorManager.hasAnchor(this)) {
                AnchorManager.removeAnchor(this);
            }
        } else {
            // если не привязан, используем якоря
            if (!AnchorManager.hasAnchor(this)) {
                AnchorManager.setAnchor(this, this.getX(), this.getY(), this.getZ());
            }

            // плавное замедление после выхода из следования
            if (!AnchorManager.isFollowing(this) && !this.isVehicle()) {
                Vec3 deceleration = AnchorManager.getDecelerationVector(this);
                if (deceleration != Vec3.ZERO) {
                    // применяем замедление, избегая ошибки нулевого вектора
                    if (deceleration.lengthSqr() > 0.0001) {
                        this.setDeltaMovement(deceleration);
                    }
                }
            }

            // улучшенное обновление якоря
            // обновляем якорь по позиции игрока каждый тик
            if (AnchorManager.isFollowing(this)) {
                Vec3 playerPos = AnchorManager.getLastPlayerPosition(this);
                if (playerPos != null) {
                    AnchorManager.createTemporaryAnchor(this, playerPos.x, playerPos.y, playerPos.z);
                }
            }

            // вертикальные ограничения
            // только если не привязан и не управляется
            if (!this.isVehicle() && !AnchorManager.isFollowing(this)) {
                Vec3 anchor = AnchorManager.getAnchor(this);
                if (anchor != null) {
                    double upperLimit = anchor.y + AnchorManager.getMaxHeightOffset(this);
                    double lowerLimit = anchor.y + AnchorManager.getMinHeightOffset(this);
                    // безопасная нижняя граница мира
                    double minWorldHeight = this.level().getMinBuildHeight() + 5.0;
                    lowerLimit = Math.max(lowerLimit, minWorldHeight);

                    if (this.getY() > upperLimit) {
                        double heightExcess = this.getY() - upperLimit;
                        double gravity = Math.min(0.08, 0.02 + (heightExcess / 100.0) * 0.06);
                        Vec3 motion = this.getDeltaMovement();
                        this.setDeltaMovement(motion.x, Math.max(motion.y - gravity, -0.12), motion.z);
                        this.hasImpulse = true;
                    } else if (this.getY() < lowerLimit) {
                        double depthExcess = lowerLimit - this.getY();
                        double lift = Math.min(0.08, 0.02 + (depthExcess / 100.0) * 0.06);
                        Vec3 motion = this.getDeltaMovement();
                        this.setDeltaMovement(motion.x, Math.min(motion.y + lift, 0.12), motion.z);
                        this.hasImpulse = true;
                    }
                }
            }
        }

        // инициализируем высоту спавна
        if (!hasInitializedSpawnHeight) {
            spawnHeight = this.getY();
            hasInitializedSpawnHeight = true;
        }

        if (inventoryComponent == null && dataComponent != null) {
            this.inventoryComponent = new GhastInventoryComponent(this, dataComponent);
        }

        if (!this.level().isClientSide) {
            if (platformComponent != null) {
                platformComponent.tick();
            }

            if (this.isVehicle()) {
                LivingEntity driver = this.getControllingPassenger();
                if (driver != null) {
                    // обновляем угол пов гаста на основе угла водителя
                    float targetYRot = driver.getYRot();
                    float targetXRot = driver.getXRot();

                    // плавная интерполяция поворота на сервере
                    float serverSmoothFactor = 0.4F;

                    // нормализуем разницу поворота
                    float yRotDiff = targetYRot - this.getYRot();
                    if (yRotDiff > 180.0F) yRotDiff -= 360.0F;
                    if (yRotDiff < -180.0F) yRotDiff += 360.0F;

                    // применяем плавный поворот
                    this.setYRot(this.getYRot() + yRotDiff * serverSmoothFactor);
                    this.setXRot(targetXRot * 0.5F); // ограничиваем вертикальный поворот

                    // обновляем поворот тела и головы
                    this.yBodyRot = this.getYRot();
                    this.yHeadRot = this.getYRot();

                    // проверяем необходимость синхронизации
                    rotationSyncCounter++;
                    if (rotationSyncCounter >= ROTATION_SYNC_INTERVAL) {
                        syncRotationToClients();
                        rotationSyncCounter = 0;
                    }
                }
            }
        }

        handleHealthRegeneration();
    }


    private void clientTick() {
        // плавная интерполяция к серверным значениям
        if (hasTargetRotation) {
            // проверяем, если игрок - водитель
            Minecraft minecraft = Minecraft.getInstance();
            boolean isDriver = minecraft.player != null &&
                    this.getPassengers().contains(minecraft.player) &&
                    this.getPassengers().indexOf(minecraft.player) == 0;

            if (!isDriver) {
                // для зрителей - плавная интерполяция
                float maxRotationStep = 3.5F; // макс скорость поворота за тик

                // нормализуем разницу поворота
                float yRotDiff = targetServerYRot - this.getYRot();
                if (yRotDiff > 180.0F) yRotDiff -= 360.0F;
                if (yRotDiff < -180.0F) yRotDiff += 360.0F;

                // применяем ограниченный шаг
                float yRotStep = Math.min(Math.abs(yRotDiff), maxRotationStep) * Math.signum(yRotDiff);
                float xRotStep = Math.min(Math.abs(targetServerXRot - this.getXRot()), maxRotationStep)
                        * Math.signum(targetServerXRot - this.getXRot());

                // применяем поворот
                this.setYRot(this.getYRot() + yRotStep);
                this.setXRot(this.getXRot() + xRotStep);

                // обновляем поворот головы и тела
                float bodyRotDiff = targetServerYBodyRot - this.yBodyRot;
                if (bodyRotDiff > 180.0F) bodyRotDiff -= 360.0F;
                if (bodyRotDiff < -180.0F) bodyRotDiff += 360.0F;

                float bodyRotStep = Math.min(Math.abs(bodyRotDiff), maxRotationStep) * Math.signum(bodyRotDiff);
                this.yBodyRot = this.yBodyRot + bodyRotStep;
                this.yHeadRot = this.yBodyRot;
            }
        }
    }

    private void tickQuadLeash() {
        if (!this.level().isClientSide && this.isQuadLeashing()) {
            // получаем список uuid
            List<UUID> targetUUIDs = getQuadLeashedEntityUUIDs();

            // проверяем на пустой список
            if (targetUUIDs.isEmpty()) {
                this.setQuadLeashing(false);
                return;
            }

            // копируем список для безопасной итерации
            List<UUID> disconnectList = new ArrayList<>();

            // обрабатываем каждую сущность
            for (UUID targetUUID : targetUUIDs) {
                Entity targetEntity = ((ServerLevel)this.level()).getEntity(targetUUID);

                // проверка валидности и дистанции
                boolean shouldBreak = false;
                if (targetEntity == null || !isValidQuadLeashTarget(targetEntity) ||
                        targetEntity.isRemoved() || targetEntity.level() != this.level()) {
                    LOGGER.debug("Quad leash target invalid or removed. Breaking leash for UUID: {}", targetUUID);
                    shouldBreak = true;
                } else {
                    // проверка дистанции
                    double distanceSqr = this.distanceToSqr(targetEntity);
                    double breakDistance = 48.0;
                    if (distanceSqr > breakDistance * breakDistance) {
                        LOGGER.debug("Quad leash distance {} exceeded break distance {}. Breaking leash for UUID: {}",
                                Math.sqrt(distanceSqr), breakDistance, targetUUID);
                        shouldBreak = true;
                    }
                }

                if (shouldBreak) {
                    disconnectList.add(targetUUID);
                    continue;
                }

                // применяем физику к цели
                // вычисляем целевую позицию под гастом
                double targetX = this.getX();
                // распределяем сущности по высоте
                double heightOffset = 6.5D;

                // если >1, увеличиваем расстояние
                if (targetUUIDs.size() > 1) {
                    // находим индекс для расчета отступа
                    int entityIndex = targetUUIDs.indexOf(targetUUID);

                    // распределяем по горизонтали
                    double angleSpacing = 2.0 * Math.PI / targetUUIDs.size();
                    double currentAngle = entityIndex * angleSpacing;

                    // горизонтальное смещение
                    double horizontalOffset = 2.0 + (targetUUIDs.size() - 1) * 0.5;

                    // применяем горизонтальное смещение
                    targetX += Math.cos(currentAngle) * horizontalOffset;
                    double targetZ = this.getZ() + Math.sin(currentAngle) * horizontalOffset;

                    // вертикальное положение одинаковое
                    double targetY = this.getY() - heightOffset;

                    // изменяем позицию в зависимости от состояния гаста
                    if (this.isDescending()) {
                        // если гаст снижается, увеличиваем расстояние
                        targetY -= 1.5D; // доп смещение вниз
                    }

                    // применяем физику
                    applyQuadLeashPhysics(targetEntity, targetX, targetY, targetZ);
                } else {
                    // стандартная позиция для одной сущности
                    double targetY = this.getY() - heightOffset;
                    if (this.isDescending()) {
                        targetY -= 1.5D;
                    }
                    double targetZ = this.getZ();

                    // применяем стандартную физику
                    applyQuadLeashPhysics(targetEntity, targetX, targetY, targetZ);
                }
            }

            boolean disconnectedAny = false;
            for (UUID uuidToDisconnect : disconnectList) {
                Entity targetEntity = ((ServerLevel)this.level()).getEntity(uuidToDisconnect);

                // очищаем обратную ссылку
                if (targetEntity instanceof IQuadLeashTarget targetAsLeashTarget) {
                    targetAsLeashTarget.setQuadLeashingGhastUUID(Optional.empty());
                }

                // удаляем из списка
                this.removeQuadLeashedEntity(uuidToDisconnect);

                // звук разрыва
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.LEASH_KNOT_BREAK, this.getSoundSource(), 0.8F, 0.8F);

                disconnectedAny = true;
            }

            // принудительная синхронизация, если что-то отсоединилось
            if (disconnectedAny) {
                this.forceSyncQuadLeashData();
            }

            // периодическая синхронизация для актуальности данных
            // используем больший интервал для уменьшения трафика
            if (this.tickCount % 200 == 0) {  // каждые 10 секунд
                this.forceSyncQuadLeashData();
            }
        } else if (!this.level().isClientSide && !this.isQuadLeashing()) {
            // очищаем все следы привязи, если флаг выключен
            this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, new CompoundTag());
        }
    }

    private void applyQuadLeashPhysics(Entity targetEntity, double targetX, double targetY, double targetZ) {
        // получаем текущую позицию
        double entityX = targetEntity.getX();
        double entityY = targetEntity.getY();
        double entityZ = targetEntity.getZ();

        // вычисляем вектор движения
        double deltaX = targetX - entityX;
        double deltaY = targetY - entityY;
        double deltaZ = targetZ - entityZ;

        // вычисляем дистанции
        double horizontalDistSq = deltaX * deltaX + deltaZ * deltaZ;
        double verticalDistSq = deltaY * deltaY;
        double totalDistSq = horizontalDistSq + verticalDistSq;

        // применяем силу притяжения
        if (totalDistSq > 0.01D) {
            // особая обработка для лодок
            if (targetEntity instanceof Boat boat) {
                // параметры физики лодки
                double boatHorizontalForce = 0.15D;
                double boatVerticalForce = 0.25D;

                // горизонтальное движение
                double horizontalDist = Math.sqrt(horizontalDistSq);
                double horizontalSpeed;

                if (horizontalDist > 2.0) {
                    // дальше - сильнее тяга
                    horizontalSpeed = 0.4D;
                } else if (horizontalDist > 0.8) {
                    // средняя дистанция - умеренная тяга
                    horizontalSpeed = boatHorizontalForce + 0.2D * (horizontalDist / 2.0);
                } else {
                    // близко - мягкие корректировки
                    horizontalSpeed = boatHorizontalForce * (horizontalDist / 0.8);
                }

                // вертикальное движение
                double verticalDist = Math.sqrt(verticalDistSq);
                double verticalSpeed;

                if (verticalDist > 1.5) {
                    // далеко - агрессивная тяга
                    verticalSpeed = Math.min(0.5D, boatVerticalForce + 0.3D * (verticalDist / 1.5));
                } else {
                    // близко - мягкая корректировка
                    verticalSpeed = boatVerticalForce * Math.max(0.3, verticalDist / 1.5);
                }

                // доп ускорение при вертикальном движении гаста
                if (this.isAscending()) {
                    verticalSpeed += 0.2D;
                } else if (this.isDescending()) {
                    verticalSpeed += 0.1D;
                }

                // особая обработка, если лодка далеко внизу
                if (deltaY > 4.0 && verticalDist > 2.0) {
                    // доп сила вверх
                    verticalSpeed *= 1.5;
                }

                // нормализуем компоненты
                double moveX = horizontalDist > 0.01 ? deltaX / horizontalDist * horizontalSpeed : 0;
                double moveZ = horizontalDist > 0.01 ? deltaZ / horizontalDist * horizontalSpeed : 0;
                double moveY = verticalDist > 0.01 ? deltaY / verticalDist * verticalSpeed : 0;

                // создаем и применяем вектор
                Vec3 newMotion = new Vec3(moveX, moveY, moveZ);

                // смешиваем с текущим движением для плавности
                Vec3 currentMotion = boat.getDeltaMovement();
                double blendFactor = 0.4;

                Vec3 blendedMotion = new Vec3(
                        currentMotion.x * (1-blendFactor) + newMotion.x * blendFactor,
                        currentMotion.y * (1-blendFactor) + newMotion.y * blendFactor,
                        currentMotion.z * (1-blendFactor) + newMotion.z * blendFactor
                );

                //  чтобы лодки не тонули
                if (blendedMotion.y > -0.05 && blendedMotion.y < 0.05 && Math.abs(deltaY) > 0.5) {
                    // если вертик движение минимально, но есть разрыв, добавляем небольшую силу вверх
                    blendedMotion = new Vec3(
                            blendedMotion.x,
                            blendedMotion.y + 0.05 * Math.signum(deltaY),
                            blendedMotion.z
                    );
                }

                // применяем итоговое движение
                boat.setDeltaMovement(blendedMotion);
                boat.hurtMarked = true;

                // добавляем поворот под направление гаста
                float targetYaw = this.getYRot();
                float boatYaw = boat.getYRot();
                float yawDiff = targetYaw - boatYaw;

                // нормализуем угол
                while (yawDiff > 180) yawDiff -= 360;
                while (yawDiff < -180) yawDiff += 360;

                // применяем поворот
                boat.setYRot(boatYaw + yawDiff * 0.1f);
            }
            // стандартная обработка
            else {
                // базовые коэффициенты
                double baseHorizontalSpeed = 0.08D;
                double baseVerticalSpeed = 0.1D;

                // сила горизонтального выравнивания
                double horizontalDist = Math.sqrt(horizontalDistSq);
                double horizontalSpeed;

                if (horizontalDist > 1.2) {
                    // на большой дистанции - быстрое притяжение
                    horizontalSpeed = 0.3D;
                } else if (horizontalDist > 0.5) {
                    // на средней - плавный подход
                    horizontalSpeed = baseHorizontalSpeed + 0.15D * (horizontalDist / 2.0);
                } else {
                    // очень близко - точная стабилизация
                    horizontalSpeed = baseHorizontalSpeed * (horizontalDist / 0.5);
                }

                // сила вертикального позиционирования
                double verticalDist = Math.sqrt(verticalDistSq);
                double verticalSpeed;

                if (verticalDist > 1.5) {
                    // большое откл по вертикали - быстрое выравнивание
                    verticalSpeed = Math.min(0.3D, baseVerticalSpeed + 0.1D * (verticalDist / 1.5));
                } else {
                    // близко к нужной высоте - точная стабилизация
                    verticalSpeed = baseVerticalSpeed * Math.max(0.2, verticalDist / 1.5);
                }

                // нормализуем компоненты
                double moveX = horizontalDist > 0.01 ? deltaX / horizontalDist * horizontalSpeed : 0;
                double moveZ = horizontalDist > 0.01 ? deltaZ / horizontalDist * horizontalSpeed : 0;
                double moveY = verticalDist > 0.01 ? deltaY / verticalDist * verticalSpeed : 0;

                // создаем итоговый вектор
                Vec3 newMotion = new Vec3(moveX, moveY, moveZ);

                // применяем движение
                targetEntity.setDeltaMovement(newMotion);
                targetEntity.hurtMarked = true;
                targetEntity.fallDistance = 0.0F;

                // обработка для живых существ
                if (targetEntity instanceof LivingEntity targetLiving) {
                    // поворот под гаста
                    targetLiving.setYRot(this.getYRot());
                    targetLiving.setXRot(this.getXRot() * 0.5F);
                    targetLiving.yBodyRot = this.yBodyRot;
                    targetLiving.yHeadRot = this.yHeadRot;

                    // если моб, останавливаем навигацию
                    if (targetEntity instanceof Mob targetMob) {
                        targetMob.getNavigation().stop();
                    }
                }
            }
        }
    }



    public static boolean isValidQuadLeashTarget(Entity entity) {
        return entity instanceof AbstractHorse || entity instanceof Camel || entity instanceof Sniffer || entity instanceof Boat;
    }

    // обновленный метод canBeQuadLeashedEntity
    public static boolean canBeQuadLeashedEntity(Entity entity) {
        return entity instanceof Boat || entity instanceof AbstractHorse || entity instanceof Camel || entity instanceof Sniffer;
    }


    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            clientTick();
        } else {
            serverTick();
        }
    }


    @Override
    public void remove(RemovalReason reason) {
        // очищаем квадро-привязь пред удалением
        if (!this.level().isClientSide && this.isQuadLeashing()) {
            clearAllQuadLeashedEntities();
        }

        // очистка якоря и платформы
        if (!this.level().isClientSide) { AnchorManager.removeAnchor(this); if (platformComponent != null) platformComponent.onOwnerRemoved(); }

        super.remove(reason); // вызываем родительский метод
    }

    // ИИ цели
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new HappyGhastFollowPlayerWithItemGoal(this, 1.0D, 30.0F));
        this.goalSelector.addGoal(5, new HappyGhastRandomFloatGoal(this));
        this.goalSelector.addGoal(7, new HappyGhastLookGoal(this));
    }

    // Атрибуты и спаунинг
    public static AttributeSupplier.Builder createAttributes() {
        return Ghast.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    //  Сохранение данных (NBT)
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);

        // сохраняем базовые данные
        compound.putBoolean("BlueHarness", isSaddled());

        compound.putDouble("SpawnHeight", spawnHeight);
        compound.putBoolean("HasInitializedSpawnHeight", hasInitializedSpawnHeight);

        // сохраняем цвет сбруи
        if (isSaddled()) {
            compound.putString("HarnessColor", getHarnessColor());
        }
        // сохраняем множитель скорости
        compound.putFloat("SpeedMultiplier", getSpeedMultiplier());
        compound.putBoolean("IsQuadLeashing", this.isQuadLeashing());
        this.getQuadLeashedEntityUUID().ifPresent(uuid -> compound.putUUID("QuadLeashedEntityUUID", uuid));
        if (dataComponent != null) {
            dataComponent.addAdditionalSaveData(compound);
        }

        if (platformComponent != null) {
            platformComponent.addAdditionalSaveData(compound);
        }
        if (inventoryComponent != null) {
            inventoryComponent.addAdditionalSaveData(compound);
        }
        CompoundTag leashedEntitiesTag = this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITIES);
        if (leashedEntitiesTag != null && !leashedEntitiesTag.isEmpty()) {
            compound.put("QuadLeashedEntities", leashedEntitiesTag.copy());
        }
    }


    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);

        if (compound.contains("SpawnHeight")) {
            spawnHeight = compound.getDouble("SpawnHeight");
        }
        if (compound.contains("HasInitializedSpawnHeight")) {
            hasInitializedSpawnHeight = compound.getBoolean("HasInitializedSpawnHeight");
        }

        // инициализация компонентов
        if (dataComponent == null) {
            initComponents();
        }
        if (compound.contains("QuadLeashedEntities")) {
            this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, compound.getCompound("QuadLeashedEntities"));
            // проверяем, есть ли привязанные сущности
            if (getQuadLeashedEntityCount() > 0) {
                this.setQuadLeashing(true);
            }
        }
        // загружаем цвет сбруи
        if (compound.contains("HarnessColor")) {
            setHarnessColor(compound.getString("HarnessColor"));
        } else if (compound.contains("BlueHarness") && compound.getBoolean("BlueHarness")) {
            setHarnessColor("blue");
        }
        this.setQuadLeashing(compound.getBoolean("IsQuadLeashing"));
        if (compound.hasUUID("QuadLeashedEntityUUID")) this.setQuadLeashedEntityUUID(Optional.of(compound.getUUID("QuadLeashedEntityUUID")));
        // загружаем множитель скорости
        if (compound.contains("SpeedMultiplier")) {
            setSpeedMultiplier(compound.getFloat("SpeedMultiplier"));
        } else {
            // если данных нет, используем глобальный множитель
            setSpeedMultiplier(GhastMovementComponent.SPEED_MULTIPLIER);
        }

        dataComponent.readAdditionalSaveData(compound);
        platformComponent.readAdditionalSaveData(compound);
        if (inventoryComponent != null) {
            inventoryComponent.readAdditionalSaveData(compound);
        }
    }

    //  Другие переопределения
    @Override
    public boolean causeFallDamage(float distance, float damageMultiplier, DamageSource source) {
        return false; // не получает урон от падения
    }

    @Override
    public void setCharging(boolean charging) {
        super.setCharging(false); // игнорируем попытки заставить стрелять
    }


    @Override
    public void setLeashedTo(Entity entity, boolean sendPacket) {
        // разрешаем привязку, если не квадро-привязан
        if ((entity instanceof Player || entity instanceof LeashFenceKnotEntity) && !this.isQuadLeashing()) {
            // для заборов - ванильная логика
            if (entity instanceof LeashFenceKnotEntity) {
                super.setLeashedTo(entity, sendPacket);
                return;
            }

            // для игроков - MultiLeashData
            if (!this.level().isClientSide) {
                MultiLeashData.addLeashHolder(this, entity);
                MultiLeashData.updateMainLeashHolder(this, entity);
            } else {
                // прямое присваивание на клиенте для реакции
                try {
                    ((MobAccessor)this).setLeashHolder(entity);
                } catch (Exception e) {
                    LOGGER.error("Client-side leash holder set failed", e);
                }
            }

            // сбрасываем квадро-привязь
            if (this.isQuadLeashing()) {
                this.setQuadLeashing(false);
                this.setQuadLeashedEntityUUID(Optional.empty());
            }
        } else if (!this.level().isClientSide) {
            // логгируем и предотвращаем некорректную привязку
            LOGGER.warn("Blocked attempt to standard-leash HappyGhast {} to {} (IsValidEntity: {}, IsQuadLeashing: {})",
                    this.getId(),
                    (entity != null ? entity.getDisplayName().getString() + "(" + entity.getId() + ")" : "null"),
                    (entity instanceof Player || entity instanceof LeashFenceKnotEntity),
                    this.isQuadLeashing());
        }
    }


    @Override
    public boolean canBeLeashed(Player player) {
        // разрешаем привязать, если не квадро-привязан и не транспорт
        boolean canStandardLeash = leashComponent != null && leashComponent.canBeLeashedByPlayer(player, platformComponent);
        return canStandardLeash && !this.isQuadLeashing() && !this.isVehicle();
    }

    @Override
    public void dropLeash(boolean broadcast, boolean dropItem) {
        // логика только на сервере для предотвращения рассинхронизации
        if (this.level().isClientSide) {
            return;
        }

        // обработка квадро-привязи
        if (this.isQuadLeashing()) {
            this.clearAllQuadLeashedEntities(); // используем полный метод очистки
            if (dropItem) {
                // выбрасываем поводок за каждую привязь
                int leashedCount = this.getQuadLeashedEntityCount();
                for (int i = 0; i < leashedCount; i++) {
                    this.spawnAtLocation(Items.LEAD);
                }
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.LEASH_KNOT_BREAK, this.getSoundSource(), 0.9F, 0.9F);
            return; // завершаем
        }

        super.dropLeash(broadcast, dropItem);
    }

    @Override
    public boolean isPersistenceRequired() {
        // гаст никогда не исчезает
        return true;
    }

    /**
     * метод для принудительного обновления сбруи
     */
    public void refreshSaddle() {
        // метод-заглушка
        // можно повторно вызвать сеттеры для обновления
        boolean currentSaddled = this.isSaddled();
        String currentColor = this.getHarnessColor();

        // можно добавить задержку
        if (this.level().isClientSide) {
            // только на клиенте
            this.setSaddled(currentSaddled);
            this.setHarnessColor(currentColor);
        }

        // дополнительно можно вызвать обновление позиции или анимации
        // this.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundInit.HAPPY_GHAST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundInit.HAPPY_GHAST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundInit.HAPPY_GHAST_DEATH.get();
    }

    public void playRideSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.HAPPY_GHAST_RIDE.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    public void playGogglesDownSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.HAPPY_GHAST_GOGGLES_DOWN.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    public void playGogglesUpSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.HAPPY_GHAST_GOGGLES_UP.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    public void playHarnessEquipSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.HAPPY_GHAST_HARNESS_EQUIP.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    public void playHarnessUnequipSound() {
        if (!this.level().isClientSide) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundInit.HAPPY_GHAST_HARNESS_UNEQUIP.get(), this.getSoundSource(), 1.0F, 1.0F);
        }
    }

    // геттер и сеттер для множителя скорости
    public float getSpeedMultiplier() {
        return this.getEntityDataValue(DATA_SPEED_MULTIPLIER);
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        this.setEntityDataValue(DATA_SPEED_MULTIPLIER, speedMultiplier);
    }

    private void handleHealthRegeneration() {
        // только на сервере
        if (this.level().isClientSide) return;

        // проверяем, нужно ли лечение
        if (this.getHealth() < this.getMaxHealth()) {
            boolean isAtCloudLevel = this.getY() >= CLOUD_LEVEL;
            boolean isRainingOrSnowing = this.level().isRaining();

            // реген на уровне облаков или в дождь/снег
            if (isAtCloudLevel || isRainingOrSnowing) {
                regenCounter++;

                // реген по интервалу
                if (regenCounter >= REGEN_INTERVAL) {
                    // лечим 1 хп (0.5 сердечка)
                    this.heal(1.0F);
                    regenCounter = 0;

                    // опционально: визуальный эффект
                    if (this.tickCount % 10 == 0) {
                        this.level().broadcastEntityEvent(this, (byte)7); // частицы сердечек
                    }
                }
            }
        } else {
            // сбрасываем счетчик при полном здоровье
            regenCounter = 0;
        }
    }

    public boolean startQuadLeash(Entity targetEntity) {
        if (!this.level().isClientSide && isValidQuadLeashTarget(targetEntity)) {
            // проверяем, можно ли добавить еще
            if (getQuadLeashedEntityCount() >= MAX_QUAD_LEASHED_ENTITIES) {
                LOGGER.warn("Cannot add more than {} quad leashed entities", MAX_QUAD_LEASHED_ENTITIES);
                return false;
            }

            // добавляем новую сущность
            boolean success = addQuadLeashedEntity(targetEntity.getUUID());

            if (success) {
                // устанавливаем обратную ссылку
                if (targetEntity instanceof IQuadLeashTarget targetAsLeashTarget) {
                    targetAsLeashTarget.setQuadLeashingGhastUUID(Optional.of(this.getUUID()));
                    //LOGGER.info("[QuadLeash DBG] Set back-ref on Target {} for Ghast {}",
                    //        targetEntity.getId(), this.getId())
                }

                // звук
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.LEASH_KNOT_PLACE, this.getSoundSource(), 1.0F, 1.0F);

                // для обратной совместимости
                if (getQuadLeashedEntityCount() == 1) {
                    this.setQuadLeashedEntityUUID(Optional.of(targetEntity.getUUID()));
                }

                // принудительная синхронизация
                forceSyncQuadLeashData();

                return true;
            }
        }

        return false;
    }

    /**
     * добавляет новую сущность в список, если есть место
     * @return true если успешно, false если лимит
     */
    public boolean addQuadLeashedEntity(UUID entityUUID) {
        if (getQuadLeashedEntityCount() >= MAX_QUAD_LEASHED_ENTITIES) {
            return false;
        }

        // получаем текущий список
        List<UUID> currentEntities = getQuadLeashedEntityUUIDs();

        // проверяем, нет ли уже этой сущности
        if (currentEntities.contains(entityUUID)) {
            return false;
        }

        // добавляем
        CompoundTag tag = this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITIES);
        if (tag == null) tag = new CompoundTag();

        // находим свободный слот
        for (int i = 0; i < MAX_QUAD_LEASHED_ENTITIES; i++) {
            String key = "entity" + i;
            if (!tag.hasUUID(key)) {
                tag.putUUID(key, entityUUID);

                // обновляем данные
                this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, tag);

                // ставим флаг
                this.setQuadLeashing(true);

                // принудительная синхронизация
                forceSyncQuadLeashData();

                return true;
            }
        }

        return false;
    }


    /**
     * удаляет сущность из списка
     * @return true если найдена и удалена
     */
    public boolean removeQuadLeashedEntity(UUID entityUUID) {
        // отслеживаем удаление
        boolean removed = false;

        // проверяем устаревшее поле
        Optional<UUID> legacyUUID = this.getQuadLeashedEntityUUID();
        if (legacyUUID.isPresent() && legacyUUID.get().equals(entityUUID)) {
            this.setQuadLeashedEntityUUID(Optional.empty());
            removed = true;
            //LOGGER.debug("Removed entity {} from legacy quad leash field", entityUUID)
        }

        // проверяем CompoundTag
        CompoundTag tag = this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITIES);
        if (tag != null) {
            for (int i = 0; i < MAX_QUAD_LEASHED_ENTITIES; i++) {
                String key = "entity" + i;
                if (tag.hasUUID(key) && tag.getUUID(key).equals(entityUUID)) {
                    // удаляем из тега
                    tag.remove(key);
                    // обновляем данные
                    this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, tag);
                    removed = true;
                    //LOGGER.debug("Removed entity {} from CompoundTag slot {}", entityUUID, i)
                    break;
                }
            }
        }

        if (removed) {
            // принудительная синхронизация
            forceSyncQuadLeashData();

            // обновляем флаг isQuadLeashing
            List<UUID> remainingEntities = getQuadLeashedEntityUUIDs();
            if (remainingEntities.isEmpty()) {
                this.setQuadLeashing(false);
                //LOGGER.debug("No more leashed entities, setting isQuadLeashing to false")
            } else {
                //LOGGER.debug("Still have {} leashed entities, keeping isQuadLeashing true", remainingEntities.size())
            }

            return true;
        }

        return false;
    }
    /**
     * очищает все привязанные сущности
     */
    public void clearAllQuadLeashedEntities() {
        if (!this.level().isClientSide) {
            // получаем и отвязываем
            List<UUID> currentUUIDs = new ArrayList<>(getQuadLeashedEntityUUIDs());

            for (UUID targetUUID : currentUUIDs) {
                Entity targetEntity = ((ServerLevel)this.level()).getEntity(targetUUID);

                // очищаем обратную ссылку
                if (targetEntity instanceof IQuadLeashTarget targetAsLeashTarget) {
                    targetAsLeashTarget.setQuadLeashingGhastUUID(Optional.empty());
                }

                // эффекты разрыва
                if (targetEntity != null) {
                    this.level().playSound(null, targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(),
                            SoundEvents.LEASH_KNOT_BREAK, this.getSoundSource(), 0.8F, 0.8F);
                }
            }
        }

        // очищаем данные
        this.setEntityDataValue(DATA_QUAD_LEASHED_ENTITIES, new CompoundTag());
        this.setQuadLeashing(false);

        // принудительная синхронизация
        forceSyncQuadLeashData();

        // для обратной совместимости
        this.setQuadLeashedEntityUUID(Optional.empty());
    }


    private void forceSyncQuadLeashData() {
        if (!this.level().isClientSide) {
            try {
                // получаем данные
                CompoundTag leashedEntitiesData = this.getEntityDataValue(DATA_QUAD_LEASHED_ENTITIES);
                boolean isQuadLeashing = this.getEntityDataValue(DATA_IS_QUAD_LEASHING);

                // убеждаемся, что данные есть
                if (leashedEntitiesData == null) {
                    leashedEntitiesData = new CompoundTag();
                }

                // добавляем временную метку для уникальности пакета
                // удалим на клиенте
                CompoundTag syncTag = leashedEntitiesData.copy();
                syncTag.putLong("syncTimestamp", System.currentTimeMillis());

                // создаем и отправляем пакет
                GhastQuadLeashSyncPacket packet = new GhastQuadLeashSyncPacket(
                        this.getId(),
                        syncTag,
                        isQuadLeashing
                );

                // отправляем только игрокам, которые отслеживают сущность
                NetworkHandler.CHANNEL.send(
                        PacketDistributor.TRACKING_ENTITY.with(() -> this),
                        packet
                );

                //LOGGER.debug("Sent quad leash sync packet with {} entities", getQuadLeashedEntityCount())
            } catch (Exception e) {
                // ловим исключения
                //LOGGER.error("Error sending quad leash sync packet", e)
            }
        }
    }

}