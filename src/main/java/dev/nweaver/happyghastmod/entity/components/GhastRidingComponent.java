package dev.nweaver.happyghastmod.entity.components;

import dev.nweaver.happyghastmod.entity.AnchorManager;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

public class GhastRidingComponent {
    private static final Logger LOGGER = LogManager.getLogger();
    private final HappyGhast owner;
    private final GhastDataComponent dataComponent;
    private final GhastPlatformComponent platformComponent;

    private static final boolean DEBUG_LOGGING = false; // логирование отключено

    public GhastRidingComponent(HappyGhast owner, GhastDataComponent dataComponent, GhastPlatformComponent platformComponent) {
        this.owner = owner;
        this.dataComponent = dataComponent;
        this.platformComponent = platformComponent;
    }

    // можно ли посадить пассажира?
    public boolean canAddPassenger(Entity passenger) {
        boolean hasSaddle = dataComponent.isSaddled();
        int maxPassengers = owner.getMaxPassengers(); // берем макс. кол-во пассажиров
        int currentPassengers = owner.getPassengers().size();
        boolean platformActive = platformComponent != null && platformComponent.isActive();

        // можно, если есть седло, есть место и платформа выключена
        return hasSaddle
                && currentPassengers < maxPassengers
                && !platformActive;
    }

    // когда пассажир слезает
    public void handlePassengerRemoval(Entity passenger) {
        // только на сервере и для игрока
        if (!owner.level().isClientSide && passenger instanceof Player player) {
            // обновляем якорь
            AnchorManager.setAnchor(owner, owner.getX(), owner.getY(), owner.getZ());

            // и высоту спавна (чтобы летал у этой точки)
            try {
                java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                java.lang.reflect.Field initializedField = HappyGhast.class.getDeclaredField("hasInitializedSpawnHeight");

                spawnHeightField.setAccessible(true);
                initializedField.setAccessible(true);

                spawnHeightField.set(owner, owner.getY());
                initializedField.set(owner, true);

                if (DEBUG_LOGGING) {
                    //LOGGER.info("Updated anchor and spawn height for HappyGhast {} at dismount location: {}, {}, {}",
                    //        owner.getId(), owner.getX(), owner.getY(), owner.getZ());
                }
            } catch (Exception e) {
                //LOGGER.warn("Could not update spawn height field in HappyGhast", e);
            }

            // если следовал за кем-то - стоп
            if (AnchorManager.isFollowing(owner)) {
                AnchorManager.stopFollowing(owner);
            }

            // звук поднятия очков
            owner.playGogglesUpSound();

            // включаем платформу, если еще не включена
            if (!platformComponent.isActive()) {
                if (owner.isLeashed()) {
                    Entity leashHolder = owner.getLeashHolder();

                    // если был привязан к забору - удаляем узел
                    if (leashHolder instanceof net.minecraft.world.entity.decoration.LeashFenceKnotEntity) {
                        leashHolder.discard();
                    }

                    // отвязываем с дропом поводка
                    owner.dropLeash(true, true);
                }

                platformComponent.activate();
            }
        }
    }

    // кто сидит за рулем?
    @Nullable
    public LivingEntity getControllingPassenger() {
        List<Entity> passengers = owner.getPassengers();
        if (passengers.isEmpty()) {
            return null;
        }

        // первый севший - водитель
        Entity firstPassenger = passengers.get(0);
        if (firstPassenger instanceof Player player) {
            return player;
        }

        // если это моб, то он рулит
        if (firstPassenger instanceof LivingEntity livingEntity) {
            return livingEntity;
        }

        // если это армор стенд и т.п. - никто не рулит
        return null;
    }

    // рассаживаем пассажиров
    public void positionRider(Entity passenger, Entity.MoveFunction moveFunction) {
        if (!owner.hasPassenger(passenger)) {
            return;
        }

        // базовая высота
        double baseYOffset = 3.8D;
        List<Entity> passengers = owner.getPassengers();
        int passengerIndex = passengers.indexOf(passenger);
        float ownerYawRad = -owner.getYRot() * Mth.DEG_TO_RAD;

        // смещения для каждого места
        double xOffset;
        double zOffset;

        if (passengerIndex == 0) {
            xOffset = 0.0D; // водитель
            zOffset = 2.0D;
        } else {
            // остальные
            if (passengerIndex == 1) {
                xOffset = 2.0D;  // справа
                zOffset = 0.0D;
            } else if (passengerIndex == 2) {
                xOffset = 0.0D;  // сзади
                zOffset = -2.0D;
            } else {
                xOffset = -2.0D; // слева
                zOffset = 0.0D;
            }
        }

        // поворачиваем смещения
        double rotatedX = xOffset * Math.cos(ownerYawRad) + zOffset * Math.sin(ownerYawRad);
        double rotatedZ = zOffset * Math.cos(ownerYawRad) - xOffset * Math.sin(ownerYawRad);

        // финальная позиция
        double finalX = owner.getX() + rotatedX;
        double finalY = owner.getY() + baseYOffset + passenger.getMyRidingOffset();
        double finalZ = owner.getZ() + rotatedZ;

        moveFunction.accept(passenger, finalX, finalY, finalZ);

        // для водителя на клиенте
        if (passengerIndex == 0 && owner.level().isClientSide) {
            Minecraft minecraft = Minecraft.getInstance();

            // если это мы - не ограничиваем камеру, а поворачиваем гаста за ней
            if (passenger == minecraft.player) {
                owner.setYRot(passenger.getYRot());
                owner.setXRot(passenger.getXRot() * 0.5F);
                owner.yBodyRot = owner.getYRot();
                owner.yHeadRot = owner.getYRot();
            } else {
                // для других - стандартно
                passenger.setYRot(owner.getYRot());
                passenger.setYBodyRot(owner.getYRot());
                if (passenger instanceof LivingEntity livingPassenger) {
                    livingPassenger.yHeadRot = owner.getYRot();
                }
            }
        } else if (passengerIndex == 0) {
            // на сервере - стандартно
            passenger.setYRot(owner.getYRot());
            passenger.setYBodyRot(owner.getYRot());
            if (passenger instanceof LivingEntity livingPassenger) {
                livingPassenger.yHeadRot = owner.getYRot();
            }
        }
    }


    // где спешиваться
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        // обновляем якорь
        if (!owner.level().isClientSide && passenger instanceof Player) {
            AnchorManager.setAnchorWithCooldown(owner, owner.getX(), owner.getY(), owner.getZ());

            // и высоту спавна
            try {
                java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                spawnHeightField.setAccessible(true);
                spawnHeightField.set(owner, owner.getY());
            } catch (Exception e) {
                // игнорим ошибки рефлексии
            }
        }

        // высота спешивания (над платформой)
        double dismountY = owner.getY() + GhastPlatformComponent.PLATFORM_Y_OFFSET + 0.5;

        // все спешиваются в центр
        return new Vec3(owner.getX(), dismountY, owner.getZ());
    }
}