package dev.nweaver.happyghastmod.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

// пакет для открытия gui создателя кастомной сбруи на клиенте
// использует side-aware код, чтобы избежать загрузки классов клиента на сервере
public class OpenHarnessCreatorPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    // флаг, указывающий является ли это одиночной игрой
    private final boolean isSinglePlayer;

    public OpenHarnessCreatorPacket(boolean isSinglePlayer) {
        this.isSinglePlayer = isSinglePlayer;
    }

    public static void encode(OpenHarnessCreatorPacket packet, FriendlyByteBuf buffer) {
        // отправляем, является ли это сервером одиночной игры
        buffer.writeBoolean(packet.isSinglePlayer);
    }

    public static OpenHarnessCreatorPacket decode(FriendlyByteBuf buffer) {
        // читаем статус одиночной игры
        boolean isSinglePlayer = buffer.readBoolean();
        return new OpenHarnessCreatorPacket(isSinglePlayer);
    }

    public static void handle(OpenHarnessCreatorPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // убедимся что мы на стороне клиента
            if (ctx.get().getDirection().getReceptionSide() == LogicalSide.CLIENT) {
                // используем distexecutor для безопасного выполнения клиентского кода
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    // открываем gui только если сервер - одиночная игра
                    if (packet.isSinglePlayer) {
                        // вызываем обработчик клиента, который загружается только на клиенте
                        ClientNetworkHandler.openHarnessCreatorGui();
                    } else {
                        LOGGER.info("Custom harness creator is disabled in multiplayer");
                    }
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}