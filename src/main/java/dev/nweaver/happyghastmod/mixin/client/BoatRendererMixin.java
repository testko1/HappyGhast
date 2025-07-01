package dev.nweaver.happyghastmod.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.client.renderer.QuadLeashRenderer;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.UUID;

// миксин для boatrenderer для рендера квадро-поводка с точки зрения лодки
// когда лодка видна, а гаст - нет
@Mixin(BoatRenderer.class)
public abstract class BoatRendererMixin extends EntityRenderer<Boat> {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoatRendererMixin.class);

    // обязательный конструктор для миксина
    protected BoatRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    // внедряемся в конец метода рендера для добавления рендера квадро-поводка
    @Inject(
            method = "render(Lnet/minecraft/world/entity/vehicle/Boat;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL") // выполнять после полного рендера лодки
    )
    private void happyghastmod$renderQuadLeashFromBoat(Boat boat, float entityYaw, float partialTicks,
                                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                                       int packedLight, CallbackInfo ci) {
        // выполнять только на стороне клиента
        if (!boat.level().isClientSide) return;

        // используем больший радиус поиска, чтобы находить гастов даже когда они далеко
        List<Entity> nearbyGhasts = findNearbyGhasts(boat, 128.0);

        for (Entity entity : nearbyGhasts) {
            if (entity instanceof HappyGhast ghast && ghast.isQuadLeashing()) {
                // получаем все uuid привязанных сущностей
                List<UUID> targetUUIDs = ghast.getQuadLeashedEntityUUIDs();

                // также проверяем legacy uuid для обратной совместимости
                ghast.getQuadLeashedEntityUUID().ifPresent(uuid -> {
                    if (!targetUUIDs.contains(uuid)) {
                        targetUUIDs.add(uuid);
                    }
                });

                // проверяем, есть ли эта лодка в списке привязанных у гаста
                if (targetUUIDs.contains(boat.getUUID())) {
                    poseStack.pushPose(); // сохраняем текущее состояние posestack

                    try {
                        // рендерим квадро-поводок с обратным направлением
                        QuadLeashRenderer.renderQuadLeash(ghast, boat, poseStack, bufferSource, partialTicks, true);

                        if (Minecraft.getInstance().player.tickCount % 600 == 0) {
                            //LOGGER.info("Rendered quad leash from Boat {} to Ghast {}",
                            //        boat.getId(), ghast.getId())
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error rendering quad leash from Boat {} to Ghast {}",
                                boat.getId(), ghast.getId(), e);
                    } finally {
                        poseStack.popPose(); // восстанавливаем состояние posestack
                    }

                    break; // нужно отрендерить только один раз если найден
                }
            }
        }
    }

    // вспомогательный метод для поиска всех счастливых гастов рядом с лодкой
    private List<Entity> findNearbyGhasts(Boat boat, double radius) {
        Level level = boat.level();
        AABB searchBox = boat.getBoundingBox().inflate(radius);

        return level.getEntities(boat, searchBox, entity -> entity instanceof HappyGhast);
    }
}