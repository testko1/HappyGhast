package dev.nweaver.happyghastmod.client.renderer.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ghast;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HappyGhastGlassesLayer extends RenderLayer<Ghast, GhastModel<Ghast>> {
    private static final Logger LOGGER = LogManager.getLogger();

    // стандартные текстуры для очков
    private static final ResourceLocation GLASSES_TEXTURE =
            HappyGhastMod.rl("textures/entity/glassestexture.png");
    private static final ResourceLocation PWGOOD_TEXTURE =
            HappyGhastMod.rl("textures/entity/pwgood_face.png");
    private static final ResourceLocation PWGOODS_TEXTURE =
            HappyGhastMod.rl("textures/entity/pwgoods_face.png");

    private final GhastModel<Ghast> model;

    // стандартное позиционирование
    private static final float Y_OFFSET = -0.10F;  // базовая позиция для очков
    private static final float Z_OFFSET = 0F;     // базовое смещение вперед
    private static final float SCALE = 1.10F;     // масштаб очков

    // стандартное позиционирование аксессуара
    private static final float ACCESSORY_Y_OFFSET = -0.0F;
    private static final float ACCESSORY_Z_OFFSET = -0.00F;
    private static final float ACCESSORY_SCALE = 1.055F;

    // смещение для поднятых очков
    private static final float RAISED_OFFSET = 0.2F;  // положительное значение опускает очки ниже

    public HappyGhastGlassesLayer(RenderLayerParent<Ghast, GhastModel<Ghast>> parent, GhastModel<Ghast> model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Ghast entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (entity instanceof HappyGhast happyGhast && happyGhast.isSaddled()) {
            // получаем цвет сбруи
            String harnessColor = happyGhast.getHarnessColor();

            // проверяем кастомную сбрую - извлекаем айди из цвета
            String customHarnessId = null;
            if (harnessColor.startsWith("custom:")) {
                customHarnessId = harnessColor.substring(7); // удаляем префикс "custom:"
            }

            // определяем текстуру очков
            ResourceLocation glassesTexture = determineGlassesTexture(harnessColor, customHarnessId);

            // используем прозрачный рендеринг для очков
            RenderType renderType = RenderType.entityTranslucent(glassesTexture);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

            poseStack.pushPose();

            // применяем масштаб
            float scale = SCALE;
            poseStack.scale(scale, scale, scale);

            // инициализируем значения позиции
            float offsetX = 0.0F;
            float offsetY = Y_OFFSET;
            float offsetZ = Z_OFFSET;
            float rotX = 0.0F;

            // проверяем, является ли цвет сбруи pwgood или pwgoods
            boolean isPwGoodType = "pwgood".equals(harnessColor) || "pwgoods".equals(harnessColor);

            // если гаст не используется как транспорт и это не pwgood/pwgoods, поднимаем очки
            if (!happyGhast.isVehicle() && !isPwGoodType) {
                offsetY += RAISED_OFFSET;
                rotX = -40.0F;
                offsetZ += 0.52F;
            }

            // применяем позицию и вращение
            poseStack.translate(offsetX, offsetY, offsetZ);
            if (rotX != 0.0F) poseStack.mulPose(Axis.XP.rotationDegrees(rotX));

            // рендерим модель
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

            poseStack.popPose();

            // рендерим аксессуар, если есть кастомная текстура
            if (customHarnessId != null &&
                    ClientCustomHarnessManager.hasCustomTexture(
                            customHarnessId, ClientCustomHarnessManager.TextureType.ACCESSORY)) {

                ResourceLocation accessoryTexture = ClientCustomHarnessManager.getHarnessTexture(
                        customHarnessId, ClientCustomHarnessManager.TextureType.ACCESSORY);

                if (accessoryTexture != null) {
                    renderAccessory(poseStack, buffer, packedLight, happyGhast, accessoryTexture);
                }
            }
        }
    }

    private ResourceLocation determineGlassesTexture(String harnessColor, String customHarnessId) {
        // сначала проверяем текстуру кастомной сбруи
        if (customHarnessId != null) {
            ResourceLocation customTexture = ClientCustomHarnessManager.getHarnessTexture(
                    customHarnessId, ClientCustomHarnessManager.TextureType.GLASSES);

            if (customTexture != null) {
                return customTexture;
            }
        }

        // в зависимости от цвета сбруи выбираем стандартную текстуру
        switch (harnessColor) {
            case "pwgood":
                return PWGOOD_TEXTURE;
            case "pwgoods":
                return PWGOODS_TEXTURE;
            default:
                return GLASSES_TEXTURE;
        }
    }

    private void renderAccessory(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                                 HappyGhast happyGhast, ResourceLocation textureToUse) {
        // создаем тип рендеринга
        RenderType renderType = RenderType.entityTranslucent(textureToUse);
        VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

        poseStack.pushPose();

        // применяем масштаб
        float scale = ACCESSORY_SCALE;
        poseStack.scale(scale, scale, scale);

        // применяем позицию и вращение
        float offsetX = 0.0F;
        float offsetY = ACCESSORY_Y_OFFSET;
        float offsetZ = ACCESSORY_Z_OFFSET;

        poseStack.translate(offsetX, offsetY, offsetZ);

        // рендерим аксессуар без копирования параметров анимации
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}