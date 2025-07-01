package dev.nweaver.happyghastmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nweaver.happyghastmod.leash.LeashAttachmentHistory;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;

@Mixin(MobRenderer.class)
public abstract class MobRendererMixin<T extends Mob, M extends net.minecraft.client.model.EntityModel<T>>
        extends LivingEntityRenderer<T, M> {

    // кэш для хранения истории точек привязки для каждого моба
    private static final Map<UUID, LeashAttachmentHistory> LEASH_HISTORY = new HashMap<>();
    // небольшой сдвиг для исправления багов с шейдерами
    private static final float SHADER_FIX_OFFSET = 0.01F;

    // конструктор
    protected MobRendererMixin(net.minecraft.client.renderer.entity.EntityRendererProvider.Context context, M model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    // перехватываем вызов оригинального renderleash
    @Redirect(method = "render(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/MobRenderer;renderLeash(Lnet/minecraft/world/entity/Mob;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;)V"))
    private void hg_redirectRenderLeash(MobRenderer<T, M> renderer, T mob, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, Entity leashHolder) {
        renderUltraSmoothLeash(mob, partialTicks, poseStack, bufferSource, leashHolder);
    }

    // метод рендеринга поводка с продвинутым сглаживанием
    private void renderUltraSmoothLeash(T mob, float partialTicks, PoseStack poseStack, MultiBufferSource bufferSource, Entity leashHolder) {
        poseStack.pushPose();

        // получаем uuid моба
        UUID mobId = mob.getUUID();

        // получаем историю точек привязки или создаем новую
        LeashAttachmentHistory history = LEASH_HISTORY.computeIfAbsent(mobId, id -> new LeashAttachmentHistory());

        // получаем позицию держателя поводка
        Vec3 holderPos = leashHolder.getRopeHoldPosition(partialTicks);

        // вычисляем базовую точку привязки поводка к мобу без учета поворота
        // используем стабильные координаты, игнорируя точные повороты моба
        Vec3 baseAttachPoint = mob.getLeashOffset(partialTicks);

        // используем мировые координаты моба
        double mobX = Mth.lerp(partialTicks, mob.xo, mob.getX());
        double mobY = Mth.lerp(partialTicks, mob.yo, mob.getY());
        double mobZ = Mth.lerp(partialTicks, mob.zo, mob.getZ());

        // берем базовую точку относительно центра моба, а не его поворота
        Vec3 localOffsetPoint = new Vec3(0, baseAttachPoint.y, 0);

        // добавляем новую точку в историю
        history.addPoint(localOffsetPoint);

        // получаем сглаженную точку
        Vec3 smoothedLocalOffset = history.getSmoothedPoint(localOffsetPoint);

        // применяем смещение к стэку позы
        poseStack.translate(smoothedLocalOffset.x, smoothedLocalOffset.y, smoothedLocalOffset.z);

        // вычисляем абсолютные координаты привязки поводка
        double attachX = mobX + smoothedLocalOffset.x;
        double attachY = mobY + smoothedLocalOffset.y;
        double attachZ = mobZ + smoothedLocalOffset.z;

        // вычисляем разницу между точкой привязки и позицией держателя
        float deltaX = (float)(holderPos.x - attachX);
        float deltaY = (float)(holderPos.y - attachY);
        float deltaZ = (float)(holderPos.z - attachZ);

        // рендерим поводок
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.leash());
        Matrix4f matrix4f = poseStack.last().pose();

        // увеличенная толщина поводка
        float thicknessScale = 2.0F;
        float widthFactor = Mth.invSqrt(deltaX * deltaX + deltaZ * deltaZ) * 0.025F / 2.0F * thicknessScale;
        float offsetX = deltaZ * widthFactor;
        float offsetZ = deltaX * widthFactor;

        // расчет освещения
        BlockPos mobBlockPos = BlockPos.containing(mob.getEyePosition(partialTicks));
        BlockPos holderBlockPos = BlockPos.containing(leashHolder.getEyePosition(partialTicks));
        int mobBlockLight = this.getBlockLightLevel(mob, mobBlockPos);
        int holderBlockLight = leashHolder.isOnFire() ? 15 : leashHolder.level().getBrightness(LightLayer.BLOCK, holderBlockPos);
        int mobSkyLight = mob.level().getBrightness(LightLayer.SKY, mobBlockPos);
        int holderSkyLight = leashHolder.level().getBrightness(LightLayer.SKY, holderBlockPos);

        // рендеринг сегментов поводка
        for (int segment = 0; segment <= 24; ++segment) {
            renderSmoothLeashSegment(vertexConsumer, matrix4f, deltaX, deltaY, deltaZ,
                    mobBlockLight, holderBlockLight, mobSkyLight, holderSkyLight,
                    0.025F * thicknessScale, 0.025F * thicknessScale,
                    offsetX, offsetZ, segment, false);
        }

        for (int segment = 24; segment >= 0; --segment) {
            renderSmoothLeashSegment(vertexConsumer, matrix4f, deltaX, deltaY, deltaZ,
                    mobBlockLight, holderBlockLight, mobSkyLight, holderSkyLight,
                    0.025F * thicknessScale, 0.0F,
                    offsetX, offsetZ, segment, true);
        }

        poseStack.popPose();

        // очищаем историю для мобов, которые больше не на поводке
        if (!mob.isLeashed()) {
            LEASH_HISTORY.remove(mobId);
        }
    }

    // рендеринг одного сегмента поводка с улучшенным провисанием
    private static void renderSmoothLeashSegment(VertexConsumer vertexConsumer, Matrix4f matrix4f,
                                                 float deltaX, float deltaY, float deltaZ,
                                                 int startBlockLight, int endBlockLight,
                                                 int startSkyLight, int endSkyLight,
                                                 float thickness1, float thickness2,
                                                 float offsetX, float offsetZ,
                                                 int segment, boolean reverse) {
        float t = (float)segment / 24.0F;

        // интерполяция освещения
        int blockLight = (int)Mth.lerp(t, (float)startBlockLight, (float)endBlockLight);
        int skyLight = (int)Mth.lerp(t, (float)startSkyLight, (float)endSkyLight);
        int packedLight = LightTexture.pack(blockLight, skyLight);

        // расчет толщины
        float thickness = thickness1 * t;
        float thickness2Value = thickness2 > 0.0F ? thickness2 * t * t : thickness2 - thickness2 * (1.0F - t) * (1.0F - t);

        // расчет x и z с линейной интерполяцией
        float x = deltaX * t;
        float z = deltaZ * t;

        float y;

        // создаем естественное провисание поводка с учетом его длины и направления
        float horizontalDistSq = deltaX * deltaX + deltaZ * deltaZ;
        float horizontalDist = Mth.sqrt(horizontalDistSq);

        // чем длиннее поводок, тем больше провисание
        float provisionFactor = horizontalDist * 0.05F;

        if (deltaY >= 0) {
            // если поводок идет вверх или горизонтально
            float catenary = -4.0F * t * (1.0F - t) * provisionFactor;
            y = deltaY * t + catenary;
        } else {
            // если поводок идет вниз
            // усиливаем провисание, чтобы оно выглядело реалистичнее
            float catenary = -4.5F * t * (1.0F - t) * provisionFactor;
            y = deltaY * (t * t * (3.0F - 2.0F * t)) + catenary;
        }

        // вычисляем точные смещения для толщины
        float finalOffsetX = reverse ? offsetX : -offsetX;
        float finalOffsetZ = reverse ? offsetZ : -offsetZ;

        // обавляем небольшой сдвиг y
        float yOffset = SHADER_FIX_OFFSET;

        // рендерим вершины
        vertexConsumer.vertex(matrix4f, x + finalOffsetX, y + thickness + yOffset, z + finalOffsetZ)
                .color(0.4F, 0.24F, 0.16F, 1.0F).uv2(packedLight).endVertex();
        vertexConsumer.vertex(matrix4f, x - finalOffsetX, y + thickness2Value + yOffset, z - finalOffsetZ)
                .color(0.4F, 0.24F, 0.16F, 1.0F).uv2(packedLight).endVertex();
    }
}