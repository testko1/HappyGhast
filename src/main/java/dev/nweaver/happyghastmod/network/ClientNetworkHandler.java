package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.client.gui.CustomHarnessCreator;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// класс для обработки сетевой функциональности на стороне клиента
// этот класс загружается только на стороне клиента
@OnlyIn(Dist.CLIENT)
public class ClientNetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    // открывает gui создателя сбруи на клиенте
    public static void openHarnessCreatorGui() {
        LOGGER.debug("Opening harness creator GUI on client");
        Minecraft.getInstance().execute(() -> {
            Minecraft.getInstance().setScreen(new CustomHarnessCreator());
        });
    }
}