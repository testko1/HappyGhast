package dev.nweaver.happyghastmod.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.client.renderer.QuadLeashRenderer;
import dev.nweaver.happyghastmod.client.renderer.QuadLeashRenderManager;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

// миксин для levelrenderer, выполняющий два важных действия:
// 1 сбрасывает флаги рендеринга в начале каждого кадра
// 2 активно рендерит все квадро-поводки в конце рендеринга мира, даже если сущности вне кадра
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    // счетчик кадров для оптимизации проверок
    private static int frameCounter = 0;

    // кэш пар гаст-цель для активного рендеринга
    private static final Map<UUID, List<UUID>> activeLeashPairs = new HashMap<>();

    // предыдущее время последнего обновления кэша
    private static long lastCacheUpdate = 0;

    // как часто обновлять кэш активных пар (в мс)
    private static final long CACHE_UPDATE_INTERVAL = 1000; // 1 секунда

    // в начале рендеринга кадра
    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("HEAD")
    )
    private void happyghastmod$onRenderLevelStart(PoseStack pPoseStack, float pPartialTick, long pFinishNanoTime, boolean pRenderBlockOutline, Camera pCamera, GameRenderer pGameRenderer, LightTexture pLightTexture, Matrix4f pProjectionMatrix, CallbackInfo ci) {
        // сбрасываем флаг рендеринга поводка для нового кадра через quadleashrenderer
        QuadLeashRenderer.startNewRenderFrame();

        // увеличиваем счетчик кадров
        frameCounter++;

        // запускаем проверку гастов каждые 5 кадров
        if (frameCounter % 5 == 0) {
            try {
                QuadLeashRenderManager.forceCheckAllGhasts();

                // периодически обновляем кэш активных пар для рендеринга
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCacheUpdate > CACHE_UPDATE_INTERVAL) {
                    updateActiveLeashPairsCache();
                    lastCacheUpdate = currentTime;
                }
            } catch (Exception e) {
                // игнорируем ошибки во избежание крашей
            }
        }
    }

    // в конце рендеринга кадра - активный рендеринг всех известных пар
    @Inject(
            method = "renderLevel(Lcom/mojang/blaze3d/vertex/PoseStack;FJZLnet/minecraft/client/Camera;Lnet/minecraft/client/renderer/GameRenderer;Lnet/minecraft/client/renderer/LightTexture;Lorg/joml/Matrix4f;)V",
            at = @At("RETURN")
    )
    private void happyghastmod$onRenderLevelEnd(PoseStack pPoseStack, float pPartialTick, long pFinishNanoTime, boolean pRenderBlockOutline, Camera pCamera, GameRenderer pGameRenderer, LightTexture pLightTexture, Matrix4f pProjectionMatrix, CallbackInfo ci) {
        // только если у нас есть активные пары и мир загружен
        if (activeLeashPairs.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }

        // получаем буфер для рендеринга
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        // сохраняем текущее состояние позиции
        pPoseStack.pushPose();

        try {
            // активно рендерим все известные пары
            renderAllActivePairs(pPoseStack, bufferSource, pPartialTick);
        } catch (Exception e) {
            // игнорируем ошибки
        } finally {
            // восстанавливаем позицию
            pPoseStack.popPose();
            // освобождаем буфер
            bufferSource.endBatch();
        }
    }

    // обновляет кэш активных пар для рендеринга
    private void updateActiveLeashPairsCache() {
        if (Minecraft.getInstance().level == null) return;

        // очищаем старый кэш
        activeLeashPairs.clear();

        // ищем всех гастов в мире с большим радиусом
        double searchRadius = 512.0; // очень большой радиус

        // пробуем искать от игрока
        Entity player = Minecraft.getInstance().player;
        if (player == null) return;

        AABB searchBox = player.getBoundingBox().inflate(searchRadius);

        // находим всех гастов
        List<HappyGhast> allGhasts = Minecraft.getInstance().level.getEntitiesOfClass(
                HappyGhast.class, searchBox
        );

        // для каждого гаста собираем информацию о привязках
        for (HappyGhast ghast : allGhasts) {
            if (!ghast.isQuadLeashing()) continue;

            List<UUID> targetUUIDs = new ArrayList<>(ghast.getQuadLeashedEntityUUIDs());

            // также проверяем устаревший метод для обратной совместимости
            ghast.getQuadLeashedEntityUUID().ifPresent(uuid -> {
                if (!targetUUIDs.contains(uuid)) {
                    targetUUIDs.add(uuid);
                }
            });

            if (!targetUUIDs.isEmpty()) {
                // добавляем пары в кэш
                activeLeashPairs.put(ghast.getUUID(), targetUUIDs);
            }
        }
    }

    // активно рендерит все известные пары, даже если они не в поле зрения
    private void renderAllActivePairs(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        if (Minecraft.getInstance().level == null) return;

        // радиус поиска для сущностей
        double searchRadius = 512.0;
        AABB worldSearchBox = Minecraft.getInstance().player.getBoundingBox().inflate(searchRadius);

        // получаем камеру и её позицию
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();

        // для каждой пары в кэше
        for (Map.Entry<UUID, List<UUID>> entry : activeLeashPairs.entrySet()) {
            UUID ghastUUID = entry.getKey();
            List<UUID> targetUUIDs = entry.getValue();

            // находим гаста в мире
            List<HappyGhast> ghasts = Minecraft.getInstance().level.getEntitiesOfClass(
                    HappyGhast.class, worldSearchBox, ghast -> ghast.getUUID().equals(ghastUUID)
            );

            if (ghasts.isEmpty()) continue;
            HappyGhast ghast = ghasts.get(0);

            // проверяем каждую цель
            for (UUID targetUUID : targetUUIDs) {
                // проверяем, не была ли эта пара уже отрендерена в этом кадре
                if (QuadLeashRenderManager.isPairRendered(ghastUUID, targetUUID)) {
                    continue;
                }

                // находим цель
                List<Entity> targets = Minecraft.getInstance().level.getEntitiesOfClass(
                        Entity.class, worldSearchBox, entity -> entity.getUUID().equals(targetUUID)
                );

                if (targets.isEmpty()) continue;
                Entity target = targets.get(0);

                // рендерим поводок
                try {
                    // сохраняем текущее состояние стека
                    poseStack.pushPose();

                    // важное изменение: используем относительные координаты от камеры, а не от игрока
                    // это предотвращает лаги при движении игрока
                    poseStack.translate(
                            -cameraPos.x,
                            -cameraPos.y,
                            -cameraPos.z
                    );

                    // вызываем рендеринг с параметром world = true
                    QuadLeashRenderer.renderQuadLeashWorldCoords(ghast, target, poseStack, bufferSource, partialTick);

                    // восстанавливаем стек
                    poseStack.popPose();
                } catch (Exception e) {
                }
            }
        }
    }
}