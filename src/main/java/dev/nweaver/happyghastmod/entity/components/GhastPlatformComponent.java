package dev.nweaver.happyghastmod.entity.components;
import dev.nweaver.happyghastmod.entity.GhastPlatformEntity;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class GhastPlatformComponent {

    // константы
    public static final double PLATFORM_Y_OFFSET = 3.65;
    private static final double STANDING_CHECK_RADIUS = 2.1;
    private static final double STANDING_CHECK_HEIGHT = 2.0;
    private static final int PLATFORM_GRACE_PERIOD_DURATION = 10;
    private static final Logger LOGGER = LogManager.getLogger();
    private static final boolean DEBUG_LOGGING = false; // логирование отключено

    private final HappyGhast owner;
    private final Level level;

    private boolean isPlatformActive = false;
    @Nullable
    private UUID platformUUID = null;
    private int platformGracePeriodTicks = 0;

    public GhastPlatformComponent(HappyGhast owner) {
        this.owner = owner;
        this.level = owner.level(); // кешируем мир
    }

    public boolean isActive() {
        return isPlatformActive;
    }

    // вызывается каждый тик, управляет платформой
    public void tick() {
        // только на сервере
        if (level.isClientSide) {
            return;
        }

        // активация, если кто-то встал сверху
        // проверяем, только если платформа выключена и гаст свободен
        if (!isPlatformActive) {
            double checkY = owner.getY() + PLATFORM_Y_OFFSET;
            AABB checkZone = new AABB(
                    owner.getX() - STANDING_CHECK_RADIUS,
                    checkY - 0.1,
                    owner.getZ() - STANDING_CHECK_RADIUS,
                    owner.getX() + STANDING_CHECK_RADIUS,
                    checkY + STANDING_CHECK_HEIGHT,
                    owner.getZ() + STANDING_CHECK_RADIUS
            );
            List<Player> playersAbove = level.getEntitiesOfClass(
                    Player.class,
                    checkZone,
                    player -> player.getVehicle() != owner && player.getY() >= checkY - 0.05
            );
            if (!playersAbove.isEmpty()) {
                if (owner.isLeashed()) {
                    owner.dropLeash(true, false);
                }
                this.activate();
                return;
            }
        }

        // если платформа уже активна
        // если выключена - выходим
        if (!isPlatformActive) {
            // чистим uuid, если остался
            if (platformUUID != null) {
                platformUUID = null;
            }
            platformGracePeriodTicks = 0;
            return;
        }

        // позиционируем платформу
        GhastPlatformEntity platform = findPlatformEntity();
        if (platform != null) {
            platform.setPos(owner.getX(), owner.getY() + PLATFORM_Y_OFFSET, owner.getZ());
            platform.setDeltaMovement(Vec3.ZERO); // стопорим саму платформу
        } else {
            //LOGGER.error("[PlatformComponent] Platform is active, but entity not found! Deactivating. Ghast: {}", owner.getUUID());
            deactivate(); // выключаем принудительно
            return;
        }

        // проверяем, не пора ли выключить
        if (platformGracePeriodTicks > 0) {
            platformGracePeriodTicks--;
        } else {
            // если время вышло, и никто не стоит - выключаем
            if (!isAnyoneStandingOnTop()) {
                deactivate();
            }
        }
    }

    // активация платформы (только сервер)
    public void activate() {
        if (level.isClientSide || isPlatformActive) return;

        isPlatformActive = true;
        platformGracePeriodTicks = PLATFORM_GRACE_PERIOD_DURATION;

        // спавним саму платформу
        GhastPlatformEntity platform = new GhastPlatformEntity(level, owner.getX(), owner.getY() + PLATFORM_Y_OFFSET, owner.getZ(), owner);
        if (level.addFreshEntity(platform)) {
            platformUUID = platform.getUUID();
        } else {
            //LOGGER.error("[PlatformComponent] Failed to spawn GhastPlatformEntity! Ghast: {}", owner.getUUID());
            // если не заспавнилась - откат
            isPlatformActive = false;
            platformGracePeriodTicks = 0;
            platformUUID = null;
        }
    }

    // деактивация платформы (только сервер)
    public void deactivate() {
        if (level.isClientSide || !isPlatformActive) return;

        isPlatformActive = false;
        platformGracePeriodTicks = 0;
        removePlatformEntity(); // удаляем энтити платформы
    }

    // ищем энтити платформы по uuid
    @Nullable
    private GhastPlatformEntity findPlatformEntity() {
        if (level instanceof ServerLevel serverLevel && platformUUID != null) {
            Entity entity = serverLevel.getEntity(platformUUID);
            if (entity instanceof GhastPlatformEntity platformEntity) {
                // доп. проверка, что платформа наша
                if (platformEntity.getOwnerUUID().isPresent() && platformEntity.getOwnerUUID().get().equals(owner.getUUID())) {
                    return platformEntity;
                } else {
                    //LOGGER.error("[PlatformComponent] Found GhastPlatformEntity, but owner UUID does not match. Disconnecting platform.");
                    // если владелец не совпал - отвязываем
                    platformUUID = null;
                    isPlatformActive = false;
                    return null;
                }
            } else if (entity != null) {
                //LOGGER.error("[PlatformComponent] Found entity with platform UUID, but it's not a GhastPlatformEntity!");
                platformUUID = null; // отвязываем битый uuid
                isPlatformActive = false;
                return null;
            }
        }

        if (platformUUID != null) {
            //LOGGER.error("[PlatformComponent] Platform UUID is set, but no entity found in level. Assuming removed.");
            platformUUID = null; // считаем, что платформа удалена
            isPlatformActive = false;
            return null;
        }

        return null; // нет uuid или мы на клиенте
    }

    // удаляем энтити платформы (только сервер)
    private void removePlatformEntity() {
        if (level.isClientSide) return;

        GhastPlatformEntity platform = findPlatformEntity();
        if (platform != null) {
            platform.discard();
        }
        platformUUID = null;
    }

    // кто-нибудь стоит на платформе?
    private boolean isAnyoneStandingOnTop() {
        if (!isPlatformActive || level.isClientSide)
            return false;

        double platformBaseY = owner.getY() + PLATFORM_Y_OFFSET;
        AABB checkZone = new AABB(
                owner.getX() - STANDING_CHECK_RADIUS, platformBaseY - 0.1, owner.getZ() - STANDING_CHECK_RADIUS,
                owner.getX() + STANDING_CHECK_RADIUS, platformBaseY + STANDING_CHECK_HEIGHT, owner.getZ() + STANDING_CHECK_RADIUS
        );

        List<Entity> entitiesInZone = level.getEntities(owner, checkZone, entity ->
                entity instanceof LivingEntity &&
                        entity.getVehicle() != owner &&
                        entity.getY() >= platformBaseY - 0.05 &&
                        !(entity instanceof GhastPlatformEntity)
        );

        return !entitiesInZone.isEmpty();
    }

    // отключать ли коллизию с этой энтити?
    public boolean shouldDisableCollisionWith(Entity otherEntity) {
        // если платформа выключена, коллизия обычная
        if (!isPlatformActive || level.isClientSide) {
            return false;
        }

        // отключаем коллизию с самой платформой
        if (otherEntity instanceof GhastPlatformEntity && otherEntity.getUUID().equals(this.platformUUID)) {
            return true;
        }

        // отключаем коллизию с теми, кто стоит наверху
        if (otherEntity instanceof LivingEntity) {
            // зона чуть шире и выше
            double checkStartY = owner.getY() + PLATFORM_Y_OFFSET - 0.5;
            double checkEndY = owner.getY() + PLATFORM_Y_OFFSET + STANDING_CHECK_HEIGHT + 0.5;

            AABB collisionCheckZone = new AABB(
                    owner.getX() - STANDING_CHECK_RADIUS, checkStartY, owner.getZ() - STANDING_CHECK_RADIUS,
                    owner.getX() + STANDING_CHECK_RADIUS, checkEndY, owner.getZ() + STANDING_CHECK_RADIUS
            );

            // если бокс пересекается с нашей зоной
            if (collisionCheckZone.intersects(otherEntity.getBoundingBox())) {
                return true;
            }
        }

        return false; // в остальных случаях коллизия включена
    }

    // когда гаста-владельца удаляют
    public void onOwnerRemoved() {
        if (!level.isClientSide) {
            removePlatformEntity(); // чистим платформу
            isPlatformActive = false;
            platformGracePeriodTicks = 0;
        }
    }

    // сохранение/загрузка
    public void addAdditionalSaveData(CompoundTag compound) {
        CompoundTag platformTag = new CompoundTag();
        platformTag.putBoolean("IsActive", isPlatformActive);
        if (platformUUID != null) {
            platformTag.putUUID("PlatformUUID", platformUUID);
        }
        platformTag.putInt("GraceTicks", platformGracePeriodTicks);
        compound.put("GhastPlatformComponent", platformTag); // пишем в nbt
    }

    public void readAdditionalSaveData(CompoundTag compound) {
        if (compound.contains("GhastPlatformComponent", CompoundTag.TAG_COMPOUND)) {
            CompoundTag platformTag = compound.getCompound("GhastPlatformComponent");
            isPlatformActive = platformTag.getBoolean("IsActive");
            if (platformTag.hasUUID("PlatformUUID")) {
                platformUUID = platformTag.getUUID("PlatformUUID");
            } else {
                platformUUID = null;
                if (isPlatformActive) {
                    isPlatformActive = false; // деактивируем
                }
            }
            platformGracePeriodTicks = platformTag.getInt("GraceTicks");
        } else {
            // если данных нет
            isPlatformActive = false;
            platformUUID = null;
            platformGracePeriodTicks = 0;
        }
    }
}