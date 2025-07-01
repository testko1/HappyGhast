package dev.nweaver.happyghastmod.events;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.renderer.GhastPlatformRenderer;
import dev.nweaver.happyghastmod.client.renderer.GhastlingRenderer;
import dev.nweaver.happyghastmod.client.renderer.HappyGhastRenderer;
import dev.nweaver.happyghastmod.init.BlockInit;
import dev.nweaver.happyghastmod.init.EntityInit;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// обработчики событий для клиента
// этот класс загружается только на стороне клиента
@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    private static final Logger LOGGER = LogManager.getLogger();


    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        LOGGER.debug("Registering entity renderers");
        event.registerEntityRenderer(EntityInit.HAPPY_GHAST.get(), HappyGhastRenderer::new);
        event.registerEntityRenderer(EntityInit.GHAST_PLATFORM.get(), GhastPlatformRenderer::new);
        event.registerEntityRenderer(EntityInit.GHASTLING.get(), GhastlingRenderer::new);
    }

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        LOGGER.info("Registering Happy Ghast key bindings");
        event.register(KeyHandler.DESCEND_KEY);
        event.register(KeyHandler.ASCEND_KEY);
    }

    @SubscribeEvent
    public static void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.debug("Setting up client render layers");
        event.enqueueWork(() -> {
            // ставим слои рендера для блоков
            ItemBlockRenderTypes.setRenderLayer(BlockInit.GHASTLING_INCUBATOR.get(), RenderType.cutout());

        });
    }
}