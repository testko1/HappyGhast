package dev.nweaver.happyghastmod;

import com.mojang.brigadier.CommandDispatcher;
import dev.nweaver.happyghastmod.command.GhastRideCommand;
import dev.nweaver.happyghastmod.command.GhastSpeedCommand;
import dev.nweaver.happyghastmod.command.HarnessCreatorCommand;
import dev.nweaver.happyghastmod.command.ListHappyGhastsCommand;
import dev.nweaver.happyghastmod.core.registration.ModBiomeModifiers;
import dev.nweaver.happyghastmod.core.registration.ModFeatures;
import dev.nweaver.happyghastmod.init.*;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(HappyGhastMod.MODID)
public class HappyGhastMod {
    public static final String MODID = "happyghastmod";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public HappyGhastMod() {
        LOGGER.info("Initializing Happy Ghast Mod");

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

        // регистрация контента мода
        EntityInit.ENTITIES.register(bus);
        ItemInit.ITEMS.register(bus);
        CreativeTabInit.TABS.register(bus);
        BlockInit.BLOCKS.register(bus);
        BlockEntityInit.BLOCK_ENTITIES.register(bus);
        ContainerInit.CONTAINERS.register(bus);
        ModFeatures.FEATURES.register(bus);
        ModBiomeModifiers.BIOME_MODIFIER_SERIALIZERS.register(bus);
        SoundInit.register(bus);

        // регистрация методов настройки
        bus.addListener(this::commonSetup);

        // регистрация клиентской настройки через distexecutor, чтобы клиентский код запускался только на клиенте
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            bus.addListener(this::clientSetup);
        });

        MinecraftForge.EVENT_BUS.register(this);

        LOGGER.info("Mod initialization complete");
    }

    // общая настройка для клиента и сервера
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Running common setup");
        // инициализация сетевого обработчика в отложенной очереди
        event.enqueueWork(() -> {
            NetworkHandler.init();
        });
    }

    // настройка только для клиента
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Running client setup");
        event.enqueueWork(() -> {
            // регистрация клиентских gui
            // импортируем этот класс только внутри метода, чтобы избежать загрузки на сервере
            net.minecraft.client.gui.screens.MenuScreens.register(
                    ContainerInit.GHAST_CONTAINER.get(),
                    dev.nweaver.happyghastmod.client.gui.GhastScreen::new
            );

            // любые другие регистрации только для клиента
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        CommandBuildContext buildContext = event.getBuildContext();
        ListHappyGhastsCommand.register(event.getDispatcher());
        GhastRideCommand.register(dispatcher, buildContext);
        HarnessCreatorCommand.register(dispatcher, buildContext);
        GhastSpeedCommand.register(dispatcher, buildContext);
    }

    public static ResourceLocation rl(String path) {
        return new ResourceLocation(MODID, path);
    }
}