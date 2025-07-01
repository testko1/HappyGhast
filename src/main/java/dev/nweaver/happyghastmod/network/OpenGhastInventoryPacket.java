package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

public class OpenGhastInventoryPacket {
    private static final Logger LOGGER = LogManager.getLogger();
    private final int entityId;

    public OpenGhastInventoryPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(OpenGhastInventoryPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
    }

    public static OpenGhastInventoryPacket decode(FriendlyByteBuf buffer) {
        return new OpenGhastInventoryPacket(buffer.readInt());
    }

    public static void handle(OpenGhastInventoryPacket packet, Supplier<NetworkEvent.Context> ctx) {
        // помечаем пакет как обработанный немедленно, чтобы предотвратить дисконнекты
        ctx.get().setPacketHandled(true);

        ctx.get().enqueueWork(() -> {
            try {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) {
                    LOGGER.warn("Received OpenGhastInventoryPacket with null player");
                    return;
                }

                if (player.level() == null) {
                    LOGGER.warn("Player level is null when processing OpenGhastInventoryPacket");
                    return;
                }

                Entity entity = player.level().getEntity(packet.entityId);
                if (entity == null) {
                    LOGGER.warn("Could not find entity with ID {} for player {}",
                            packet.entityId, player.getName().getString());
                    return;
                }

                if (!(entity instanceof HappyGhast)) {
                    LOGGER.warn("Entity with ID {} is not a HappyGhast", packet.entityId);
                    return;
                }

                HappyGhast ghast = (HappyGhast) entity;
                if (!ghast.isAlive()) {
                    LOGGER.debug("Cannot open inventory for dead ghast");
                    return;
                }

                if (!ghast.hasPassenger(player)) {
                    LOGGER.debug("Player {} is not riding ghast {}",
                            player.getName().getString(), packet.entityId);
                    return;
                }

                // когда все проверки пройдены, открываем инвентарь
                LOGGER.debug("Opening inventory for ghast {} and player {}",
                        packet.entityId, player.getName().getString());
                ghast.getInventoryComponent().openInventory(player);

            } catch (Exception e) {
                LOGGER.error("Error processing OpenGhastInventoryPacket: {}", e.getMessage(), e);
            }
        });
    }
}