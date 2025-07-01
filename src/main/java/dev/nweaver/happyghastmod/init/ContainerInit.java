package dev.nweaver.happyghastmod.init;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.container.GhastContainer;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ContainerInit {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final DeferredRegister<MenuType<?>> CONTAINERS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, HappyGhastMod.MODID);

    public static final RegistryObject<MenuType<GhastContainer>> GHAST_CONTAINER =
            CONTAINERS.register("ghast_container",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        LOGGER.debug("Creating client GhastContainer with data buffer");
                        return GhastContainer.create(windowId, inv, data);
                    }));
}