package dev.nweaver.happyghastmod.client.renderer.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Ghast;

public class HappyGhastQuadLeashVisualLayer extends RenderLayer<Ghast, GhastModel<Ghast>> {

    // используем текстуру веревок или создаем новую для индикатора квадро-привязи
    private static final ResourceLocation QUAD_VISUAL_TEXTURE =
            HappyGhastMod.rl("textures/entity/happy_ghast_ropes.png");

    private final GhastModel<Ghast> model;
    private static final float Y_OFFSET = -0.05F;

    public HappyGhastQuadLeashVisualLayer(RenderLayerParent<Ghast, GhastModel<Ghast>> parent, GhastModel<Ghast> model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       Ghast entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        if (entity instanceof HappyGhast happyGhast && happyGhast.isQuadLeashing()) {
            // используем правильную текстуру
            RenderType renderType = RenderType.entityTranslucent(QUAD_VISUAL_TEXTURE);
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);

            // рендеринг модели (как раньше)
            poseStack.pushPose();
            poseStack.scale(1.05F, 1.05F, 1.05F);
            poseStack.translate(0, Y_OFFSET, 0);
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
            poseStack.popPose();
        }
    }
}