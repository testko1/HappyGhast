package dev.nweaver.happyghastmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.entity.GhastPlatformEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

// пустой рендерер, который ничего не рисует
public class GhastPlatformRenderer extends EntityRenderer<GhastPlatformEntity> {

    public GhastPlatformRenderer(EntityRendererProvider.Context context) {
        super(context);
        // устанавливаем тень в 0, чтобы она не рендерилась
        this.shadowRadius = 0.0F;
    }

    // этот метод оставляем пустым, чтобы ничего не рендерилось
    @Override
    public void render(GhastPlatformEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // ничего не делаем здесь
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight); // вызов super важен для некоторых базовых вещей, как рендер огня, но тень мы отключили
    }

    // возвращаем null или любую текстуру, она все равно не будет использоваться, тк render пустой
    @Override
    public ResourceLocation getTextureLocation(GhastPlatformEntity entity) {
        return null; // или какая-нибудь дефолтная пустая текстура, если null вызовет проблемы
    }

    // отключаем стандартную проверку видимости, чтобы игра не тратила ресурсы зря
    // (хотя shouldrender все равно будет вызван, но он уже не упадет)
    @Override
    protected boolean shouldShowName(GhastPlatformEntity pEntity) {
        return false;
    }
}