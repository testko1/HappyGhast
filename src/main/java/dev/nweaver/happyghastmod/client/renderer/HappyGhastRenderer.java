package dev.nweaver.happyghastmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.model.HappyGhastModel;
import dev.nweaver.happyghastmod.client.renderer.layers.HappyGhastGlassesLayer;
import dev.nweaver.happyghastmod.client.renderer.layers.HappyGhastQuadLeashVisualLayer;
import dev.nweaver.happyghastmod.client.renderer.layers.HappyGhastSaddleLayer;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.init.ModelLayersInit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.GhastModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.GhastRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Ghast;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HappyGhastRenderer extends GhastRenderer {

    private static final ResourceLocation TEXTURE =
            HappyGhastMod.rl("textures/entity/happy_ghast.png");
    private static final Logger LOGGER = LogManager.getLogger(HappyGhastRenderer.class);

    // храним независимую модель для слоев, которая не будет анимироваться
    protected final GhastModel<Ghast> staticLayerModel;

    public HappyGhastRenderer(EntityRendererProvider.Context ctx) {
        super(ctx); // конструктор суперкласса инициализирует thismodel стандартной ghastmodel

        // создаем отдельную статичную модель для слоев
        this.staticLayerModel = new HappyGhastModel<>(ctx.bakeLayer(ModelLayersInit.HAPPY_GHAST));

        // используем staticlayermodel
        this.addLayer(new HappyGhastSaddleLayer(this, this.staticLayerModel));
        this.addLayer(new HappyGhastGlassesLayer(this, this.staticLayerModel));
        this.addLayer(new HappyGhastQuadLeashVisualLayer(this, this.staticLayerModel));

        // основная модель для рендеринга самого гаста
        this.model = new HappyGhastModel<>(ctx.bakeLayer(ModelLayersInit.HAPPY_GHAST));

        System.out.println("[HappyGhastMod] HappyGhastRenderer initialized with Fresh Animations compatibility!");
    }

    @Override
    public void render(Ghast entity, float entityYaw, float partialTicks, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        // логика интерполяции углов
        if (entity instanceof HappyGhast happyGhast) {
            float interpolatedYaw = Mth.rotLerp(partialTicks, happyGhast.yRotO, happyGhast.getYRot());
            boolean isLocalPlayerDriver = false;
            if (happyGhast.isVehicle() && Minecraft.getInstance().player != null) {
                if (happyGhast.getPassengers().indexOf(Minecraft.getInstance().player) == 0) {
                    isLocalPlayerDriver = true;
                }
            }
            if (happyGhast.isVehicle() && !isLocalPlayerDriver) {
                happyGhast.yBodyRot = interpolatedYaw;
                happyGhast.yHeadRot = interpolatedYaw;
            }
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Ghast entity) {
        return TEXTURE;
    }
}