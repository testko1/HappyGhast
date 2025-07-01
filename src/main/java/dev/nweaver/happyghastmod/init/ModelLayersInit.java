package dev.nweaver.happyghastmod.init;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.model.GhastlingModel;
import dev.nweaver.happyghastmod.client.model.HappyGhastModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;



@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModelLayersInit {

    public static final ModelLayerLocation HAPPY_GHAST =
            new ModelLayerLocation(HappyGhastMod.rl("happy_ghast"), "main");
    public static final ModelLayerLocation GHASTLING =
            new ModelLayerLocation(HappyGhastMod.rl("ghastling"), "main");
    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(HAPPY_GHAST, HappyGhastModel::createBodyLayer);
        event.registerLayerDefinition(GHASTLING, GhastlingModel::createBodyLayer);
    }
}