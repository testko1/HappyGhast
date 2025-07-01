package dev.nweaver.happyghastmod.client;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import dev.nweaver.happyghastmod.network.OpenGhastInventoryPacket;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// клиентский обработчик инвентаря гаста
@OnlyIn(Dist.CLIENT)
public class ClientGhastInventoryHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    // обработка ввода для открытия инвентаря и отправка пакета на сервер
    public static void handleClientKeyInput(Entity entity) {
        if (entity instanceof HappyGhast ghast && ghast.level().isClientSide) {
            LOGGER.debug("Sending request to open ghast inventory for entity ID: {}", ghast.getId());
            NetworkHandler.sendToServer(new OpenGhastInventoryPacket(ghast.getId()));
        }
    }
}