package dev.nweaver.happyghastmod.entity.components;
import dev.nweaver.happyghastmod.entity.AnchorManager;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GhastMovementComponent {
    private static final Logger LOGGER = LogManager.getLogger();

    private final HappyGhast owner;
    private final GhastDataComponent dataComponent;
    private final GhastPlatformComponent platformComponent;
    private final GhastRidingComponent ridingComponent;

    // множитель скорости (из команды)
    public static float SPEED_MULTIPLIER = 1.0F;

    // базовая скорость (чуть меньше оригинала)
    private static final float BASE_FORWARD_SPEED = 0.18F;
    // (оригинал для справки)
    private static final float ORIGINAL_FORWARD_SPEED = 0.3F;
    // (тоже уменьшено)
    private static final float MAX_VERTICAL_SPEED = 0.3F;
    private static final double MIN_SPEED_THRESHOLD = 1e-3;
    private static final double VERTICAL_MOVEMENT_RANGE = 30.0;

    private int rideSoundCounter = 0;
    private static final int RIDE_SOUND_INTERVAL = 80;

    // для плавного ускорения
    private static final float ACCELERATION_RATE = 0.1F; // (чем меньше, тем плавнее)
    private Vec3 targetVelocity = Vec3.ZERO; // куда хотим лететь

    public GhastMovementComponent(HappyGhast owner, GhastDataComponent dataComponent,
                                  GhastPlatformComponent platformComponent, GhastRidingComponent ridingComponent) {
        this.owner = owner;
        this.dataComponent = dataComponent;
        this.platformComponent = platformComponent;
        this.ridingComponent = ridingComponent;
    }

    private float getSpeedMultiplier() {
        return owner.getSpeedMultiplier();
    }

    // обработка движения гаста
    public void handleTravel(Vec3 travelVector) {
        if (!owner.isAlive()) {
            return;
        }

        // если платформа активна - стоп
        if (!owner.level().isClientSide && platformComponent.isActive()) {
            targetVelocity = Vec3.ZERO;
            owner.setDeltaMovement(Vec3.ZERO);
            owner.getNavigation().stop();
            return;
        }

        // если гастом управляют
        LivingEntity controller = ridingComponent.getControllingPassenger();
        if (owner.isVehicle() && controller instanceof Player player) {
            handleRiderControlledMovement(player); // передаем управление
            return;
        }

        // логика удержания высоты
        if (!owner.level().isClientSide) {
            double spawnHeight = 0;
            boolean hasInitializedHeight = false;

            if (owner instanceof HappyGhast) {
                HappyGhast happyGhast = (HappyGhast) owner;
                // получаем высоту спавна через рефлексию, тк private
                try {
                    java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                    java.lang.reflect.Field initializedField = HappyGhast.class.getDeclaredField("hasInitializedSpawnHeight");

                    spawnHeightField.setAccessible(true);
                    initializedField.setAccessible(true);

                    spawnHeight = (double) spawnHeightField.get(happyGhast);
                    hasInitializedHeight = (boolean) initializedField.get(happyGhast);
                } catch (Exception e) {
                    // фоллбэк
                    spawnHeight = owner.getY();
                    //LOGGER.error("Error accessing spawn height fields", e);
                }
            } else {
                spawnHeight = owner.getY();
            }

            if (!hasInitializedHeight) {
                spawnHeight = owner.getY();
            }

            // границы полета
            double upperLimit = spawnHeight + VERTICAL_MOVEMENT_RANGE;
            double lowerLimit = spawnHeight - VERTICAL_MOVEMENT_RANGE;

            // защита для суперплоскости
            double minWorldHeight = owner.level().getMinBuildHeight() + 5.0;
            lowerLimit = Math.max(lowerLimit, minWorldHeight);

            Vec3 motion = owner.getDeltaMovement();
            boolean needCorrection = false;

            // если улетел слишком высоко - толкаем вниз
            if (owner.getY() > upperLimit) {
                double heightExcess = owner.getY() - upperLimit;
                double gravity = Math.min(0.06, 0.02 + (heightExcess / 100.0) * 0.04);
                motion = new Vec3(motion.x, Math.max(motion.y - gravity, -0.12), motion.z);
                needCorrection = true;
            }
            // если улетел слишком низко - толкаем вверх
            else if (owner.getY() < lowerLimit) {
                double depthExcess = lowerLimit - owner.getY();
                double lift = Math.min(0.06, 0.02 + (depthExcess / 100.0) * 0.04);
                motion = new Vec3(motion.x, Math.min(motion.y + lift, 0.12), motion.z);
                needCorrection = true;
            }

            if (needCorrection) {
                owner.setDeltaMovement(motion);
                owner.hasImpulse = true;
            }
        }
    }

    // движение под управлением игрока
    private void handleRiderControlledMovement(Player player) {
        // берем множитель скорости этого гаста
        float speedMultiplier = getSpeedMultiplier();

        // если это наш игрок на клиенте
        boolean isLocalPlayer = owner.level().isClientSide &&
                Minecraft.getInstance().player == player;

        // для него делаем резкий поворот
        if (isLocalPlayer) {
            owner.setYRot(player.getYRot());
            owner.setXRot(player.getXRot() * 0.5F);
            owner.yBodyRot = owner.getYRot();
            owner.yHeadRot = owner.yBodyRot;
        } else {
            // для остальных - плавный поворот
            float yRotDiff = player.getYRot() - owner.getYRot();
            if (yRotDiff > 180.0F) yRotDiff -= 360.0F;
            if (yRotDiff < -180.0F) yRotDiff += 360.0F;

            float smoothFactor = owner.level().isClientSide ? 0.5F : 0.3F;
            owner.setYRot(owner.getYRot() + yRotDiff * smoothFactor);
            owner.setXRot(player.getXRot() * 0.5F);
            owner.yBodyRot = owner.getYRot();
            owner.yHeadRot = owner.yBodyRot;
        }

        float forwardInput = player.zza;
        float sideInput = player.xxa;
        double verticalMotion = 0.0;

        // можно ли лететь вверх?
        boolean canAscend = true;
        if (!owner.level().isClientSide && dataComponent.isAscending()) {
            int groundY = findGroundHeight();
            // если есть земля, считаем от нее
            if (groundY != -1) {
                if (owner.getY() > groundY + 16.0) {
                    canAscend = false;
                }
                // если земли нет, считаем от высоты мира
            } else {
                if (owner.getY() > owner.level().getMinBuildHeight() + 48) {
                    canAscend = false;
                }
            }

            // показываем игроку частицами, что выше нельзя
            if (!canAscend && owner.tickCount % 20 == 0) {
                owner.level().addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        owner.getX(), owner.getY() + 1.5, owner.getZ(), 0, 0.05, 0);
            }
        }

        if (dataComponent.isAscending() && canAscend) {
            verticalMotion = MAX_VERTICAL_SPEED * speedMultiplier;
        } else if (dataComponent.isDescending() || player.isShiftKeyDown()) {
            verticalMotion = -MAX_VERTICAL_SPEED * speedMultiplier;
        }

        boolean hasHorizontalInput = forwardInput != 0 || sideInput != 0;
        boolean hasVerticalInput = verticalMotion != 0;

        // проигрываем звук полета периодически
        if (!owner.level().isClientSide && (hasHorizontalInput || hasVerticalInput)) {
            rideSoundCounter++;
            if (rideSoundCounter >= RIDE_SOUND_INTERVAL) {
                owner.playRideSound();
                rideSoundCounter = 0;
            }
        }

        if (hasHorizontalInput || hasVerticalInput) {
            // считаем целевую скорость
            double yawRadians = Math.toRadians(-owner.getYRot());
            double motionX = 0;
            double motionZ = 0;

            if (forwardInput != 0) {
                motionX += Math.sin(yawRadians) * forwardInput * BASE_FORWARD_SPEED * speedMultiplier;
                motionZ += Math.cos(yawRadians) * forwardInput * BASE_FORWARD_SPEED * speedMultiplier;
            }

            // движение вбок
            if (sideInput != 0) {
                motionX += Math.sin(yawRadians + (Math.PI / 2)) * sideInput * BASE_FORWARD_SPEED * speedMultiplier;
                motionZ += Math.cos(yawRadians + (Math.PI / 2)) * sideInput * BASE_FORWARD_SPEED * speedMultiplier;
            }

            targetVelocity = new Vec3(motionX, verticalMotion, motionZ);
            Vec3 currentVelocity = owner.getDeltaMovement();

            // плавно идем к целевой скорости
            float accelerationFactor = isLocalPlayer ? ACCELERATION_RATE * 1.5F : ACCELERATION_RATE;

            // если скорость высокая, ускоряемся быстрее
            if (speedMultiplier > 1.0F) {
                accelerationFactor *= Math.min(speedMultiplier, 5.0F) / 2.0F;
            }

            Vec3 newVelocity = currentVelocity.add(
                    (targetVelocity.x - currentVelocity.x) * accelerationFactor,
                    (targetVelocity.y - currentVelocity.y) * accelerationFactor,
                    (targetVelocity.z - currentVelocity.z) * accelerationFactor
            );
            owner.setDeltaMovement(newVelocity);

            // запасной вариант движения
            Vec3 fallbackMovement = new Vec3(
                    sideInput * speedMultiplier * BASE_FORWARD_SPEED / ORIGINAL_FORWARD_SPEED,
                    verticalMotion,
                    forwardInput * speedMultiplier * BASE_FORWARD_SPEED / ORIGINAL_FORWARD_SPEED
            );
            owner.applyControlledMovement(fallbackMovement);
        } else {
            // если игрок ничего не жмет - плавно тормозим
            targetVelocity = Vec3.ZERO;
            Vec3 currentVelocity = owner.getDeltaMovement();

            // фактор замедления
            Vec3 newVelocity = currentVelocity.multiply(0.94, 0.94, 0.94);

            if (newVelocity.lengthSqr() < MIN_SPEED_THRESHOLD) {
                newVelocity = Vec3.ZERO;
            }
            owner.setDeltaMovement(newVelocity);
            owner.applyControlledMovement(Vec3.ZERO);

            // если стоим на месте и слишком высоко - потихоньку опускаемся
            if (!owner.level().isClientSide) {
                int groundY = findGroundHeight();
                double maxY = (groundY != -1) ? groundY + 16.0 : owner.level().getMinBuildHeight() + 48;
                if (owner.getY() > maxY + 4.0) {
                    double heightExcess = owner.getY() - maxY;
                    double gravity = Math.min(0.04, 0.01 + (heightExcess / 100.0) * 0.03);
                    newVelocity = new Vec3(newVelocity.x, Math.max(newVelocity.y - gravity, -0.1), newVelocity.z);
                    owner.setDeltaMovement(newVelocity);
                }
            }
        }
    }


    // можно ли толкнуть гаста?
    public boolean isPushable() {
        return owner.level().isClientSide || !platformComponent.isActive();
    }

    // можно ли столкнуться с другой сущностью?
    public boolean canCollideWith(Entity otherEntity) {
        return !platformComponent.shouldDisableCollisionWith(otherEntity);
    }

    // ищем высоту земли под гастом
    // @return Y земли или -1
    private int findGroundHeight() {
        int entityX = Mth.floor(this.owner.getX());
        int entityZ = Mth.floor(this.owner.getZ());
        int startY = Mth.floor(this.owner.getY()) - 1;
        // для суперплоскости ищем глубже
        int minY = Math.max(this.owner.level().getMinBuildHeight(), startY - 256);

        // ищем сверху вниз
        for (int y = startY; y >= minY; y--) {
            BlockPos blockPos = new BlockPos(entityX, y, entityZ);
            BlockState blockState = this.owner.level().getBlockState(blockPos);

            if (!blockState.isAir() && !blockState.liquid()) {
                VoxelShape collisionShape = blockState.getCollisionShape(this.owner.level(), blockPos);
                if (!collisionShape.isEmpty()) {
                    return y + 1;
                }
            }
        }
        return -1;
    }
}