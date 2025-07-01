package dev.nweaver.happyghastmod.client.renderer.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.item.CustomHarnessItem;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class HappyGhastSaddleLayer extends RenderLayer<Ghast, GhastModel<Ghast>> {
    private static final Logger LOGGER = LogManager.getLogger("HappyGhastMod");

    // кэш для текстур сбруи
    private static final Map<String, ResourceLocation> SADDLE_TEXTURES = new HashMap<>();

    // синяя сбруя как запасной вариант
    private static final ResourceLocation BLUE_SADDLE_TEXTURE =
            HappyGhastMod.rl("textures/entity/ghastsaddleblue.png");

    private final GhastModel<Ghast> model;
    private static final float Y_OFFSET = -0.05F;

    public HappyGhastSaddleLayer(RenderLayerParent<Ghast, GhastModel<Ghast>> parent, GhastModel<Ghast> model) {
        super(parent);
        this.model = model;
    }

    // получает текстуру для сбруи определенного цвета
    private ResourceLocation getSaddleTexture(String color) {
        // проверка на null или пустоту
        if (color == null || color.isEmpty()) {
            return BLUE_SADDLE_TEXTURE;
        }

        // специальная обработка для кастомных сбруй
        if (color.startsWith("custom:")) {
            String harnessId = color.substring(7); // удаляем префикс custom

            // получаем кастомную текстуру, если она доступна
            ResourceLocation customTexture = ClientCustomHarnessManager.getHarnessTexture(
                    harnessId, ClientCustomHarnessManager.TextureType.SADDLE);

            if (customTexture != null) {
                return customTexture;
            }

            // используем синюю по умолчанию, если нет кастомной текстуры
            return BLUE_SADDLE_TEXTURE;
        }

        // для стандартных цветов
        return SADDLE_TEXTURES.computeIfAbsent(color, k -> {
            // полный путь к текстуре
            String fileName = "textures/entity/ghastsaddle" + color.replace("_", "") + ".png";
            return HappyGhastMod.rl(fileName);
        });
    }


    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Ghast entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (entity instanceof HappyGhast happyGhast && happyGhast.isSaddled()) {
            String harnessColor = happyGhast.getHarnessColor();

            // проверяем кастомную сбрую
            String customHarnessId = null;
            if (happyGhast.getInventoryComponent() != null) {
                ItemStack saddleItem = happyGhast.getInventoryComponent().getItem(0);
                if (saddleItem != null && !saddleItem.isEmpty()) {
                    if (saddleItem.getItem() instanceof CustomHarnessItem) {
                        customHarnessId = CustomHarnessItem.getCustomHarnessId(saddleItem);
                    } else if (saddleItem.hasTag() && saddleItem.getTag().contains("CustomHarnessId")) {
                        customHarnessId = saddleItem.getTag().getString("CustomHarnessId");
                    }
                }
            }

            if (customHarnessId != null) {
                harnessColor = "custom:" + customHarnessId;
            }

            // получаем текстуру сбруи для этого цвета
            ResourceLocation textureLocation = getSaddleTexture(harnessColor);

            // rendertype с правильной текстурой
            RenderType renderType = RenderType.entityTranslucent(textureLocation);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

            poseStack.pushPose();

            // масштабирование и смещение
            poseStack.scale(1.05F, 1.05F, 1.05F);
            poseStack.translate(0, Y_OFFSET, 0);

            // рендерим модель
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

            poseStack.popPose();
        }
    }
}