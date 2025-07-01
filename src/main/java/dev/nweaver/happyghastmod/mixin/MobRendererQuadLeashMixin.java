package dev.nweaver.happyghastmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.client.renderer.QuadLeashRenderer; // импортируем наш рендерер
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.server.level.ServerLevel; // нужен для поиска сущности на сервере, но рендер на клиенте
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

// миксин для mobrenderer, отвечающий за вызов рендера квадро-поводка,
// когда рендерится сам счастливый гаст
@Mixin(MobRenderer.class)
public abstract class MobRendererQuadLeashMixin<T extends Mob, M extends EntityModel<T>> extends LivingEntityRenderer<T, M> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MobRendererQuadLeashMixin.class);

    protected MobRendererQuadLeashMixin(EntityRendererProvider.Context context, M model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(
            method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL") // выполняем после основного рендера моба
    )
    private void happyghastmod$renderQuadLeashIfGhast(T mobEntity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {

        // проверяем, является ли рендерящийся моб нашим гастом и он квадро-привязан
        if (mobEntity instanceof HappyGhast ghast && ghast.isQuadLeashing()) {
            // сначала сбрасываем отслеживание рендеринга для нового кадра
            QuadLeashRenderer.startNewRenderFrame();

            // получаем список всех привязанных uuid
            List<UUID> targetUUIDs = ghast.getQuadLeashedEntityUUIDs();

            // для обратной совместимости проверяем и старый метод
            Optional<UUID> legacyTargetUUID = ghast.getQuadLeashedEntityUUID();
            if (legacyTargetUUID.isPresent() && !targetUUIDs.contains(legacyTargetUUID.get())) {
                targetUUIDs.add(legacyTargetUUID.get());
            }

            // увеличенный радиус поиска для всех целей
            AABB searchBox = ghast.getBoundingBox().inflate(256.0);

            // рендерим поводки для каждого найденного uuid
            for (UUID targetUUID : targetUUIDs) {
                // ищем целевую сущность на клиенте (тк рендеринг происходит на клиенте)
                Entity targetEntity = findTargetClient(ghast.level(), targetUUID, searchBox);

                // если цель найдена и не удалена, вызываем наш рендер поводка
                if (targetEntity != null && !targetEntity.isRemoved()) {
                    try {
                        QuadLeashRenderer.renderQuadLeash(ghast, targetEntity, poseStack, bufferSource, partialTicks);
                    } catch (Exception e) {
                        // ловим возможные ошибки рендеринга, чтобы не крашить игру
                        LOGGER.error("Error rendering quad leash from Ghast {} to Target {}", ghast.getId(), targetEntity.getId(), e);
                    }
                }
            }
        }
    }

    // вспомогательный метод для поиска целевой сущности на клиенте по uuid
    @Nullable
    private static Entity findTargetClient(Level level, UUID targetUUID, AABB searchBox) {
        // на клиенте нет прямого метода getEntity(uuid), поэтому ищем перебором
        List<Entity> nearbyEntities = level.getEntities((Entity)null, searchBox, entity -> entity.getUUID().equals(targetUUID));

        if (!nearbyEntities.isEmpty()) {
            Entity potentialTarget = nearbyEntities.get(0);
            // проверяем, подходит ли найденная сущность по типу
            if (HappyGhast.canBeQuadLeashedEntity(potentialTarget)) {
                return potentialTarget;
            }
            // если проверка не прошла, возвращаем null
            // LOGGER.warn("Found entity {} by UUID, but it cannot be quad leashed.", targetUUID);
            return null;
        }
        // не нашли сущность
        return null;
    }
}