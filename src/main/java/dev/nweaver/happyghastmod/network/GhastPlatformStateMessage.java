package dev.nweaver.happyghastmod.network;

import java.util.function.Supplier;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

// сетевое сообщение для синхронизации состояния платформы гаста
public class GhastPlatformStateMessage {
    private final int entityId;
    private final boolean isStationaryPlatform;

    // создает новое сообщение о состоянии платформы
    public GhastPlatformStateMessage(int entityId, boolean isStationaryPlatform) {
        this.entityId = entityId;
        this.isStationaryPlatform = isStationaryPlatform;
    }

    // кодирует сообщение в буфер
    public static void encode(GhastPlatformStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.entityId);
        buffer.writeBoolean(message.isStationaryPlatform);
    }

    // декодирует сообщение из буфера
    public static GhastPlatformStateMessage decode(FriendlyByteBuf buffer) {
        return new GhastPlatformStateMessage(
                buffer.readInt(),
                buffer.readBoolean()
        );
    }

    // обрабатывает полученное сообщение
    public static void handle(GhastPlatformStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            // получаем сущность по айди
            Entity entity = context.getSender().level().getEntity(message.entityId);

            // проверяем, что сущность существует и является счастливым гастом
            if (entity instanceof HappyGhast happyGhast) {
                // устанавливаем состояние платформы
                if (message.isStationaryPlatform) {
                    // гаст должен быть неподвижным
                    happyGhast.setDeltaMovement(0, 0, 0);
                }
            }
        });

        context.setPacketHandled(true);
    }
}