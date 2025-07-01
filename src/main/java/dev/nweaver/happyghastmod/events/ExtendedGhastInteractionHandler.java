package dev.nweaver.happyghastmod.events;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.network.ExtendedGhastInteractionPacket;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID)
public class ExtendedGhastInteractionHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    // увеличенная дистанция взаимодействия
    private static final double EXTENDED_INTERACTION_DISTANCE = 10.0D;

    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();

        // проверяем только если игрок смотрит в определенном направлении
        if (player.level().isClientSide()) {
            // получаем вектор взгляда игрока
            Vec3 eyePosition = player.getEyePosition();
            Vec3 lookVector = player.getViewVector(1.0F);
            Vec3 reachVector = eyePosition.add(lookVector.x * EXTENDED_INTERACTION_DISTANCE,
                    lookVector.y * EXTENDED_INTERACTION_DISTANCE,
                    lookVector.z * EXTENDED_INTERACTION_DISTANCE);

            // создаем aabb для области поиска (луч взгляда с запасом)
            AABB searchBox = new AABB(
                    Math.min(eyePosition.x, reachVector.x) - 1.0D,
                    Math.min(eyePosition.y, reachVector.y) - 1.0D,
                    Math.min(eyePosition.z, reachVector.z) - 1.0D,
                    Math.max(eyePosition.x, reachVector.x) + 1.0D,
                    Math.max(eyePosition.y, reachVector.y) + 1.0D,
                    Math.max(eyePosition.z, reachVector.z) + 1.0D
            );

            // находим всех хэппи гастов в прямой видимости
            player.level().getEntitiesOfClass(HappyGhast.class, searchBox, entity -> {
                // проверяем что сущность в пределах расширенной дистанции
                double distSq = entity.distanceToSqr(eyePosition);
                if (distSq > EXTENDED_INTERACTION_DISTANCE * EXTENDED_INTERACTION_DISTANCE) {
                    return false;
                }

                // проверяем находится ли гаст на линии взгляда
                AABB entityBox = entity.getBoundingBox().inflate(0.5D); // немного увеличил хитбокс для удобства
                Vec3 entityCenter = entityBox.getCenter();

                // вектор от глаз игрока до центра сущности
                Vec3 toEntity = entityCenter.subtract(eyePosition);
                double length = toEntity.length();

                // нормализация вектора
                toEntity = toEntity.normalize();

                // косинус угла между вектором взгляда и вектором к сущности
                double dot = toEntity.dot(lookVector);

                // если косинус большой (угол маленький), игрок смотрит на сущность
                return dot > 0.95D; // примерно 18 градусов в каждую сторону
            }).stream().min((e1, e2) ->
                    Double.compare(e1.distanceToSqr(eyePosition), e2.distanceToSqr(eyePosition))
            ).ifPresent(ghast -> {
                // отправляем сетевой пакет на сервер
                dev.nweaver.happyghastmod.network.NetworkHandler.sendToServer(
                        new dev.nweaver.happyghastmod.network.ExtendedGhastInteractionPacket(ghast.getId())
                );
            });
        }
    }

    // обработчик на сервере для завершения взаимодействия
    public static void handleExtendedInteraction(Player player, Entity entity) {
        if (entity instanceof HappyGhast ghast) {
            // проверяем дистанцию на сервере для безопасности
            double distanceSq = player.distanceToSqr(entity);
            if (distanceSq <= EXTENDED_INTERACTION_DISTANCE * EXTENDED_INTERACTION_DISTANCE) {
                // выполняем взаимодействие
                InteractionResult result = ghast.mobInteract(player, InteractionHand.MAIN_HAND);

                // логируем результат если нужно
                if (result == InteractionResult.SUCCESS) {
                    LOGGER.debug("Extended interaction successful with ghast {} at distance {}",
                            ghast.getId(), Math.sqrt(distanceSq));
                }
            }
        }
    }
}