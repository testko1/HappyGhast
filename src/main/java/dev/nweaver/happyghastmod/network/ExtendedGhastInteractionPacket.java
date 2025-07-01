package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.events.ExtendedGhastInteractionHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExtendedGhastInteractionPacket {
    private final int entityId;

    public ExtendedGhastInteractionPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(ExtendedGhastInteractionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
    }

    public static ExtendedGhastInteractionPacket decode(FriendlyByteBuf buffer) {
        return new ExtendedGhastInteractionPacket(buffer.readInt());
    }

    public static void handle(ExtendedGhastInteractionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // убедимся что находимся на сервере
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                Entity entity = level.getEntity(packet.entityId);

                if (entity != null) {
                    // передаем обработку взаимодействия в обработчик
                    ExtendedGhastInteractionHandler.handleExtendedInteraction(player, entity);
                }
            }
        });
        context.setPacketHandled(true);
    }
}