package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.entity.components.GhastMovementComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class GhastSpeedSyncPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final float speedMultiplier;

    public GhastSpeedSyncPacket(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    // метод для кодирования пакета
    public static void encode(GhastSpeedSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.speedMultiplier);
    }

    // метод для декодирования пакета
    public static GhastSpeedSyncPacket decode(FriendlyByteBuf buffer) {
        return new GhastSpeedSyncPacket(buffer.readFloat());
    }

    // обработчик пакета
    public static void handle(GhastSpeedSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // этот код выполняется в потоке игры
            //LOGGER.info("Received GhastSpeedSyncPacket with multiplier: {}", packet.speedMultiplier);

            // устанавливаем множитель скорости в статическую переменную для обратной совместимости
            GhastMovementComponent.SPEED_MULTIPLIER = packet.speedMultiplier;

            // на клиенте обновляем множитель скорости для всех существующих счастливых гастов
            if (Minecraft.getInstance().level != null) {
                for (Entity entity : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (entity instanceof HappyGhast happyGhast) {
                        happyGhast.setSpeedMultiplier(packet.speedMultiplier);
                    }
                }
            }

            LOGGER.info("Updated speed multiplier to: {}", packet.speedMultiplier);
        });

        context.setPacketHandled(true);
    }
}