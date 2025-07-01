package dev.nweaver.happyghastmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
// импорты для ротации
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class QuadLeashRenderer {

    // небольшой сдвиг для исправления шейдеров
    private static final float SHADER_FIX_OFFSET = 0.01F;

    // храним пары гаст-цель, которые уже были отрендерены в этом кадре
    public static void startNewRenderFrame() {
        QuadLeashRenderManager.startNewRenderFrame();

        // периодически проверяем всех гастов для обновления данных
        if (Minecraft.getInstance().player != null &&
                Minecraft.getInstance().player.tickCount % 20 == 0) { // раз в секунду
            QuadLeashRenderManager.forceCheckAllGhasts();
        }
    }

    // рендерит квадро-поводок между гастом и целевой сущностью
    public static <E extends Entity> void renderQuadLeash(
            HappyGhast ghast, E targetEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource, float partialTicks,
            boolean isReversedRendering) {

        UUID ghastUUID = ghast.getUUID();
        UUID targetUUID = targetEntity.getUUID();
        if (QuadLeashRenderManager.isPairRendered(ghastUUID, targetUUID)) {
            return;
        }
        QuadLeashRenderManager.markPairRendered(ghastUUID, targetUUID);
        // получаем позиции
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        // мировая позиция гаста (нужна для расчета относительной позиции цели и для света)
        double ghastX = Mth.lerp(partialTicks, ghast.xo, ghast.getX());
        double ghastY = Mth.lerp(partialTicks, ghast.yo, ghast.getY());
        double ghastZ = Mth.lerp(partialTicks, ghast.zo, ghast.getZ());
        // мировая позиция цели
        double targetX = Mth.lerp(partialTicks, targetEntity.xo, targetEntity.getX());
        double targetY = Mth.lerp(partialTicks, targetEntity.yo, targetEntity.getY());
        double targetZ = Mth.lerp(partialTicks, targetEntity.zo, targetEntity.getZ());

        // вычисляем точки крепления гаста с вращением седла
        // 1 определяем локальные координаты углов неповернутого седла относительно центра гаста
        // горизонтальное смещение от центра
        float attachPointHorizontalOffset = 2.2F;
        // вертикальное смещение от центра
        float attachPointVerticalOffset = -0.25F;

        Vec3[] localUnrotatedCorners = {
                new Vec3(-attachPointHorizontalOffset, attachPointVerticalOffset, -attachPointHorizontalOffset), // перед-лево (-x, -z)
                new Vec3( attachPointHorizontalOffset, attachPointVerticalOffset, -attachPointHorizontalOffset), // перед-право (+x, -z)
                new Vec3( attachPointHorizontalOffset, attachPointVerticalOffset,  attachPointHorizontalOffset), // зад-право (+x, +z)
                new Vec3(-attachPointHorizontalOffset, attachPointVerticalOffset,  attachPointHorizontalOffset)  // зад-лево (-x, +z)
        };

        // 2 получаем интерполированный угол поворота тела гаста (вокруг y)
        float ghastBodyYaw = Mth.lerp(partialTicks, ghast.yBodyRotO, ghast.yBodyRot);
        // конвертируем в радианы
        // в майнкрафте вращение по y часто по часовой стрелке, поэтому используем минус
        float yawRad = -ghastBodyYaw * Mth.DEG_TO_RAD;

        // 3 создаем кватернион вращения
        Quaternionf rotation = new Quaternionf().rotateY(yawRad);

        // 4 поворачиваем локальные точки крепления
        Vec3[] ghastAttachPointsRelative = new Vec3[4]; // это будут точки относительно центра гаста, но повернутые
        for (int i = 0; i < 4; i++) {
            // создаем vector3f для трансформации
            Vector3f cornerVec = new Vector3f(
                    (float)localUnrotatedCorners[i].x,
                    (float)localUnrotatedCorners[i].y,
                    (float)localUnrotatedCorners[i].z
            );
            // применяем вращение
            rotation.transform(cornerVec);
            // сохраняем результат как vec3
            ghastAttachPointsRelative[i] = new Vec3(cornerVec.x(), cornerVec.y(), cornerVec.z());
        }

        // вычисляем точки крепления на цели
        Vec3[] targetAttachPointsWorld = calculateTargetAttachPoints(targetEntity, partialTicks);

        // подготовка к рендерингу
        Matrix4f pose = poseStack.last().pose(); // матрица поз (уже включает позицию и вращение)
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.leash());

        // расчёт света (используя мировые позиции)
        // примерная мировая точка крепления гаста для света (среднее y повернутых точек)
        float avgGhastAttachYRelative = (float)(ghastAttachPointsRelative[0].y + ghastAttachPointsRelative[1].y +
                ghastAttachPointsRelative[2].y + ghastAttachPointsRelative[3].y) / 4.0f;
        BlockPos ghastLightPos = BlockPos.containing(ghastX, ghastY + avgGhastAttachYRelative, ghastZ);
        // используем верхнюю центральную точку цели для расчета света
        BlockPos targetLightPos = BlockPos.containing(targetX, targetAttachPointsWorld[0].y, targetZ);
        int ghastBlockLight = getBlockLightLevel(ghast, ghastLightPos);
        int targetBlockLight = getBlockLightLevel(targetEntity, targetLightPos);
        int ghastSkyLight = ghast.level().getBrightness(LightLayer.SKY, ghastLightPos);
        int targetSkyLight = targetEntity.level().getBrightness(LightLayer.SKY, targetLightPos);

        // рендеринг 4 веревок в зависимости от направления
        if (isReversedRendering) {
            // мы рендерим с точки зрения цели (лодка или сущность)
            // нужно инвертировать направление рендерингаб, чтобы избежать перевернутого вида
            // проблема в том, что posestack теперь относителен к цели, а не к гасту
            // нам нужно соответственно трансформировать координаты
            // вычисляем относительные позиции от гаста к цели в мировом пространстве
            double relX = ghastX - targetX;
            double relY = ghastY - targetY;
            double relZ = ghastZ - targetZ;

            for (int i = 0; i < 4; i++) {
                // точка крепления цели уже в мировом пространстве
                Vec3 targetPointWorld = targetAttachPointsWorld[i];
                // конвертируем в относительное пространство
                Vec3 targetPointRelative = targetPointWorld.subtract(targetX, targetY, targetZ);

                // получаем точку крепления гаста, скорректированную для рендера относительно цели
                Vec3 ghastPoint = ghastAttachPointsRelative[i].add(relX, relY, relZ);

                // вычисляем дельту между точками (цель к гасту для обратного рендера)
                float dx = (float)(ghastPoint.x - targetPointRelative.x);
                float dy = (float)(ghastPoint.y - targetPointRelative.y);
                float dz = (float)(ghastPoint.z - targetPointRelative.z);

                // рендерим сегменты с поменянными значениями света и точками
                renderSingleLeashSegmented(
                        vertexConsumer, pose,
                        (float)targetPointRelative.x, (float)targetPointRelative.y, (float)targetPointRelative.z, // начинаем у цели
                        dx, dy, dz, // дельта к гасту
                        targetBlockLight, ghastBlockLight, targetSkyLight, ghastSkyLight // поменянные значения света
                );
            }
        } else {
            // обычный рендер с точки зрения гаста
            for (int i = 0; i < 4; i++) {
                // старт: повернутая точка на гасте относительно центра гаста
                Vec3 startRelative = ghastAttachPointsRelative[i];

                // конец: точка на цели относительно центра гаста
                Vec3 endRelative = targetAttachPointsWorld[i].subtract(ghastX, ghastY, ghastZ);

                // дельта между началом и концом (в координатах posestack)
                float dx = (float)(endRelative.x - startRelative.x);
                float dy = (float)(endRelative.y - startRelative.y);
                float dz = (float)(endRelative.z - startRelative.z);

                // рендерим сегменты, используя старт и дельту относительно posestack
                renderSingleLeashSegmented(
                        vertexConsumer, pose,
                        (float)startRelative.x, (float)startRelative.y, (float)startRelative.z, // начинаем у гаста
                        dx, dy, dz, // дельта к цели
                        ghastBlockLight, targetBlockLight, ghastSkyLight, targetSkyLight // обычные значения света
                );
            }
        }
    }

    public static <E extends Entity> void renderQuadLeash(
            HappyGhast ghast, E targetEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource, float partialTicks) {
        // по умолчанию нормальное направление рендера (от гаста)
        renderQuadLeash(ghast, targetEntity, poseStack, bufferSource, partialTicks, false);
    }

    // вычисляет точки крепления для цели в зависимости от типа сущности
    private static Vec3[] calculateTargetAttachPoints(Entity targetEntity, float partialTicks) {
        // базовая позиция цели
        double targetX = Mth.lerp(partialTicks, targetEntity.xo, targetEntity.getX());
        double targetY = Mth.lerp(partialTicks, targetEntity.yo, targetEntity.getY());
        double targetZ = Mth.lerp(partialTicks, targetEntity.zo, targetEntity.getZ());

        // улучшенный метод получения угла поворота
        float targetYaw;

        if (targetEntity instanceof LivingEntity livingEntity) {
            // для живых сущностей, предпочитаем ybodyrot, так как он более стабилен
            targetYaw = Mth.lerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
        } else {
            // для неживых сущностей, как лодки, используем базовый yrot
            targetYaw = Mth.lerp(partialTicks, targetEntity.yRotO, targetEntity.getYRot());
        }

        // конвертируем в радианы
        float yawRad = -targetYaw * Mth.DEG_TO_RAD;

        // параметры по умолчанию для большинства сущностей
        float width = targetEntity.getBbWidth();
        float height = targetEntity.getBbHeight();
        float length = width; // по умолчанию равно ширине
        float topOffset = 0.0f; // смещение от верха сущности

        // параметры для конкретных типов сущностей
        if (targetEntity instanceof Boat) {
            // для лодок - более широкая форма, точки крепления по углам
            width = width * 0.8f;
            length = width * 1.2f;
            topOffset = 0.1f; // немного выше верха лодки для лучшей видимости
        } else if (targetEntity instanceof AbstractHorse || targetEntity instanceof Camel) {
            // для лошадей и верблюдов - удлиненное тело, точки крепления чуть ниже верха
            width = width * 0.55f;
            length = width * 1.6f;
            topOffset = -0.4f; // немного ниже верха
        } else if (targetEntity instanceof Sniffer) {
            // для нюхачей - широкое тело, точки крепления на спине
            width = width * 0.8f;
            length = width * 1.5f;
            topOffset = -0.3f; // крепим к спине, а не к верху
        } else {
            // для других сущностей - точки по краям хитбокса
            width = width * 0.65f;
            length = width * 1.1f;
            topOffset = -0.15f;
        }

        // половинные значения для расчета углов
        float halfWidth = width / 2.0f;
        float halfLength = length / 2.0f;

        // вычисляем y позицию точек крепления (верх модели + смещение)
        float attachY = (float)targetY + height + topOffset;

        // 1 определяем локальные координаты углов неповернутой модели (аналогично гасту)
        Vec3[] localUnrotatedCorners = {
                new Vec3(-halfWidth, 0, -halfLength), // перед-лево (-x, -z)
                new Vec3(halfWidth, 0, -halfLength),  // перед-право (+x, -z)
                new Vec3(halfWidth, 0, halfLength),   // зад-право (+x, +z)
                new Vec3(-halfWidth, 0, halfLength)   // зад-лево (-x, +z)
        };

        // 2 создаем кватернион вращения (как для гаста)
        Quaternionf rotation = new Quaternionf().rotateY(yawRad);

        // 3 поворачиваем локальные точки крепления (как для гаста)
        Vec3[] attachPoints = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            // создаем vector3f для трансформации
            Vector3f cornerVec = new Vector3f(
                    (float)localUnrotatedCorners[i].x,
                    (float)localUnrotatedCorners[i].y,
                    (float)localUnrotatedCorners[i].z
            );

            // применяем вращение
            rotation.transform(cornerVec);

            // конвертируем в мировые координаты
            attachPoints[i] = new Vec3(
                    targetX + cornerVec.x(),
                    attachY,
                    targetZ + cornerVec.z()
            );
        }

        return attachPoints;
    }

    private static int getBlockLightLevel(Entity entity, BlockPos pos) {
        return entity.isOnFire() ? 15 : entity.level().getBrightness(LightLayer.BLOCK, pos);
    }

    private static void renderSingleLeashSegmented(VertexConsumer consumer, Matrix4f pose,
                                                   float startX, float startY, float startZ,
                                                   float dx, float dy, float dz,
                                                   int startBlockLight, int endBlockLight,
                                                   int startSkyLight, int endSkyLight) {
        int segments = 24;
        float thicknessScale = 2.0F;
        float thicknessX = 0.025F * thicknessScale;
        float thicknessY1 = 0.025F * thicknessScale;
        float thicknessY2 = 0.0F;
        float r = 0.4F, g = 0.24F, b = 0.16F, a = 1.0F;

        float overallHorizDist = Mth.sqrt(dx * dx + dz * dz);
        float invOverallHorizDist = (overallHorizDist > 1.0E-6F) ? (1.0F / overallHorizDist) : 0.0F;
        float perpOffsetScale = invOverallHorizDist * thicknessX / 2.0F;
        float offsetX = dz * perpOffsetScale;
        float offsetZ = -dx * perpOffsetScale;

        for (int j = 0; j <= segments; ++j) {
            float t = (float) j / segments;
            addLeashVertexPair(consumer, pose, startX, startY, startZ, dx, dy, dz,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight, t,
                    offsetX, offsetZ, thicknessY1, thicknessY2, r, g, b, a, false);
        }
        for (int j = segments; j >= 0; --j) {
            float t = (float) j / segments;
            addLeashVertexPair(consumer, pose, startX, startY, startZ, dx, dy, dz,
                    startBlockLight, endBlockLight, startSkyLight, endSkyLight, t,
                    offsetX, offsetZ, thicknessY1, thicknessY2, r, g, b, a, true);
        }
    }

    private static void addLeashVertexPair(VertexConsumer consumer, Matrix4f pose,
                                           float startX, float startY, float startZ,
                                           float dx, float dy, float dz,
                                           int startBlockLight, int endBlockLight,
                                           int startSkyLight, int endSkyLight,
                                           float t,
                                           float offsetX, float offsetZ,
                                           float thicknessY1, float thicknessY2,
                                           float r, float g, float b, float a, boolean reverse) {
        float x = startX + dx * t;
        float sagAmount = 4.0f * t * (1.0f - t);
        float horizontalDist = Mth.sqrt(dx * dx + dz * dz);
        float sagIntensity = horizontalDist * 0.08f; // уменьшенное провисание
        float y_sag = -sagAmount * sagIntensity;
        float y = startY + dy * t + y_sag;
        float z = startZ + dz * t;

        int blockLight = (int) Mth.lerp(t, (float) startBlockLight, (float) endBlockLight);
        int skyLight = (int) Mth.lerp(t, (float) startSkyLight, (float) endSkyLight);
        int packedLight = LightTexture.pack(blockLight, skyLight);

        float yOff1 = reverse ? thicknessY2 : thicknessY1;
        float yOff2 = reverse ? thicknessY1 : thicknessY2;

        // добавляем небольшой сдвиг y
        float yOffset = SHADER_FIX_OFFSET;

        consumer.vertex(pose, x + offsetX, y + yOff1 + yOffset, z + offsetZ).color(r, g, b, a).uv2(packedLight).endVertex();
        consumer.vertex(pose, x - offsetX, y + yOff2 + yOffset, z - offsetZ).color(r, g, b, a).uv2(packedLight).endVertex();
    }

    // рендерит квадро-поводок между гастом и целью в мировых координатах
    // используется для рендеринга из levelrenderermixin
    public static <E extends Entity> void renderQuadLeashWorldCoords(
            HappyGhast ghast, E targetEntity,
            PoseStack poseStack,
            MultiBufferSource bufferSource, float partialTicks) {

        UUID ghastUUID = ghast.getUUID();
        UUID targetUUID = targetEntity.getUUID();

        // отмечаем пару как отрендеренную
        QuadLeashRenderManager.markPairRendered(ghastUUID, targetUUID);

        try {
            // проверка действительности сущностей
            if (ghast.isRemoved() || targetEntity.isRemoved()) {
                return;
            }

            // обновляем кэш для использования в будущих кадрах
            QuadLeashRenderManager.updateEntityCache(ghast);
            QuadLeashRenderManager.updateEntityCache(targetEntity);

            // получаем позиции как мировые координаты
            double ghastX = Mth.lerp(partialTicks, ghast.xo, ghast.getX());
            double ghastY = Mth.lerp(partialTicks, ghast.yo, ghast.getY());
            double ghastZ = Mth.lerp(partialTicks, ghast.zo, ghast.getZ());

            double targetX = Mth.lerp(partialTicks, targetEntity.xo, targetEntity.getX());
            double targetY = Mth.lerp(partialTicks, targetEntity.yo, targetEntity.getY());
            double targetZ = Mth.lerp(partialTicks, targetEntity.zo, targetEntity.getZ());

            // проверка дистанции
            double distanceSq = ghast.distanceToSqr(targetEntity);
            double maxRenderDistSq = 512.0 * 512.0;

            if (distanceSq > maxRenderDistSq) {
                return;
            }

            // вычисляем точки крепления гаста с правильными относительными позициями
            // 1 определяем точки крепления на гасте
            float attachPointHorizontalOffset = 2.2F;
            float attachPointVerticalOffset = -0.25F;

            Vec3[] localUnrotatedCorners = {
                    new Vec3(-attachPointHorizontalOffset, attachPointVerticalOffset, -attachPointHorizontalOffset),
                    new Vec3( attachPointHorizontalOffset, attachPointVerticalOffset, -attachPointHorizontalOffset),
                    new Vec3( attachPointHorizontalOffset, attachPointVerticalOffset,  attachPointHorizontalOffset),
                    new Vec3(-attachPointHorizontalOffset, attachPointVerticalOffset,  attachPointHorizontalOffset)
            };

            // 2 получаем угол поворота гаста - используем правильную интерполяцию
            float ghastBodyYaw = Mth.lerp(partialTicks, ghast.yBodyRotO, ghast.yBodyRot);
            float yawRad = -ghastBodyYaw * Mth.DEG_TO_RAD;

            // 3 создаём кватернион поворота
            Quaternionf rotation = new Quaternionf().rotateY(yawRad);

            // 4 поворачиваем точки крепления
            Vec3[] ghastAttachPointsWorld = new Vec3[4];
            for (int i = 0; i < 4; i++) {
                Vector3f cornerVec = new Vector3f(
                        (float)localUnrotatedCorners[i].x,
                        (float)localUnrotatedCorners[i].y,
                        (float)localUnrotatedCorners[i].z
                );
                rotation.transform(cornerVec);

                // переводим в мировые координаты
                ghastAttachPointsWorld[i] = new Vec3(
                        ghastX + cornerVec.x(),
                        ghastY + cornerVec.y(),
                        ghastZ + cornerVec.z()
                );
            }

            // 5 получаем точки крепления на цели, используя правильные методы
            Vec3[] targetAttachPointsWorld = calculateTargetAttachPoints(targetEntity, partialTicks);

            // подготовка к рендерингу
            Matrix4f pose = poseStack.last().pose();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.leash());

            // расчёт освещения
            BlockPos ghastLightPos = BlockPos.containing(ghastX, ghastY, ghastZ);
            BlockPos targetLightPos = BlockPos.containing(targetX, targetY, targetZ);
            int ghastBlockLight = getBlockLightLevel(ghast, ghastLightPos);
            int targetBlockLight = getBlockLightLevel(targetEntity, targetLightPos);
            int ghastSkyLight = ghast.level().getBrightness(LightLayer.SKY, ghastLightPos);
            int targetSkyLight = targetEntity.level().getBrightness(LightLayer.SKY, targetLightPos);

            // рендерим 4 поводка с более плавной анимацией
            for (int i = 0; i < 4; i++) {
                // начало: точка на гасте в мировых координатах
                Vec3 startWorld = ghastAttachPointsWorld[i];

                // конец: точка на цели в мировых координатах
                Vec3 endWorld = targetAttachPointsWorld[i];

                // дельта между началом и концом в мировых координатах
                float dx = (float)(endWorld.x - startWorld.x);
                float dy = (float)(endWorld.y - startWorld.y);
                float dz = (float)(endWorld.z - startWorld.z);

                // рендерим сегменты
                renderSingleLeashSegmented(
                        vertexConsumer, pose,
                        (float)startWorld.x, (float)startWorld.y, (float)startWorld.z,
                        dx, dy, dz,
                        ghastBlockLight, targetBlockLight, ghastSkyLight, targetSkyLight
                );
            }
        } catch (Exception e) {
        }
    }
}