package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class GhastVerticalMovementPacket {
    private final boolean isAscending;
    private final boolean isDescending;

    public GhastVerticalMovementPacket(boolean isAscending, boolean isDescending) {
        this.isAscending = isAscending;
        this.isDescending = isDescending;
    }

    // кодирование пакета
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBoolean(isAscending);
        buffer.writeBoolean(isDescending);
    }

    // декодирование пакета
    public static GhastVerticalMovementPacket decode(FriendlyByteBuf buffer) {
        return new GhastVerticalMovementPacket(buffer.readBoolean(), buffer.readBoolean());
    }

    // обработка пакета на сервере
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // получаем игрока, отправившего пакет
            var player = ctx.get().getSender();
            if (player != null) {
                // получаем транспортное средство игрока
                Entity vehicle = player.getVehicle();
                // если это счастливый гаст, обновляем состояние движения
                if (vehicle instanceof HappyGhast happyGhast) {
                    happyGhast.setAscending(isAscending);
                    happyGhast.setDescending(isDescending);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}