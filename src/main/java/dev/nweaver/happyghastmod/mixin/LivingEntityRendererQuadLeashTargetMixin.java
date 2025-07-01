package dev.nweaver.happyghastmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.client.renderer.QuadLeashRenderer;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
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

// миксин для рендера квадро-поводков с точки зрения привязанных сущностей
// когда гаст вне поля зрения, но сущность видна
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererQuadLeashTargetMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger(LivingEntityRendererQuadLeashTargetMixin.class);

    @Inject(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    private void happyghastmod$renderQuadLeashIfTarget(LivingEntity livingEntity, float entityYaw, float partialTicks,
                                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                                       int packedLight, CallbackInfo ci) {
        // запускать только на клиенте и только для определенных типов сущностей
        if (!livingEntity.level().isClientSide) return;

        // проверять квадро-поводок только если сущность - валидный тип цели
        if (!(livingEntity instanceof AbstractHorse ||
                livingEntity instanceof Camel ||
                livingEntity instanceof Sniffer)) {
            return;
        }

        Level level = livingEntity.level();
        // используем большой радиус поиска, чтобы находить гастов даже когда они далеко
        AABB searchBox = livingEntity.getBoundingBox().inflate(128.0);
        List<HappyGhast> nearbyGhasts = level.getEntitiesOfClass(HappyGhast.class, searchBox);

        for (HappyGhast ghast : nearbyGhasts) {
            if (ghast != null && !ghast.isRemoved() && ghast.isQuadLeashing()) {
                // получаем все uuid привязанных сущностей - ключевое улучшение
                List<UUID> targetUUIDs = ghast.getQuadLeashedEntityUUIDs();

                // также проверяем legacy uuid для обратной совместимости
                ghast.getQuadLeashedEntityUUID().ifPresent(uuid -> {
                    if (!targetUUIDs.contains(uuid)) {
                        targetUUIDs.add(uuid);
                    }
                });

                // проверяем, есть ли эта сущность в списке привязанных у гаста
                if (targetUUIDs.contains(livingEntity.getUUID())) {
                    poseStack.pushPose(); // сохраняем текущее состояние posestack

                    try {
                        // рендерим квадро-поводок с точки зрения цели
                        QuadLeashRenderer.renderQuadLeash(ghast, livingEntity, poseStack, bufferSource, partialTicks, true);

                        if (Minecraft.getInstance().player.tickCount % 600 == 0) {
                            //LOGGER.info("Rendered quad leash from [{}] to Ghast [{}]",
                            //        livingEntity.getType().getDescriptionId(), ghast.getId());
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error rendering quad leash from [{}] to Ghast [{}]",
                                livingEntity.getType().getDescriptionId(), ghast.getId(), e);
                    } finally {
                        poseStack.popPose(); // восстанавливаем состояние posestack
                    }

                    break; // нужно отрендерить только для одного гаста
                }
            }
        }
    }
}