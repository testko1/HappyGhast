package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.custom.CustomHarnessManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

// пакет для регистрации кастомной сбруи с текстурами
public class RegisterCustomHarnessPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final String harnessId;
    private final String harnessName;
    private final byte[] saddleTextureData;
    private final byte[] glassesTextureData;
    private final byte[] accessoryTextureData;

    public RegisterCustomHarnessPacket(String harnessId, String harnessName,
                                       byte[] saddleTextureData,
                                       byte[] glassesTextureData, byte[] accessoryTextureData) {
        this.harnessId = harnessId;
        this.harnessName = harnessName;
        this.saddleTextureData = saddleTextureData;
        this.glassesTextureData = glassesTextureData;
        this.accessoryTextureData = accessoryTextureData;
    }

    public static void encode(RegisterCustomHarnessPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.harnessId);
        buffer.writeUtf(packet.harnessName);

        // записываем текстуры с проверкой на null
        writeNullableByteArray(buffer, packet.saddleTextureData);
        writeNullableByteArray(buffer, packet.glassesTextureData);
        writeNullableByteArray(buffer, packet.accessoryTextureData);
    }

    public static RegisterCustomHarnessPacket decode(FriendlyByteBuf buffer) {
        String harnessId = buffer.readUtf();
        String harnessName = buffer.readUtf();

        // читаем текстуры
        byte[] saddleTextureData = readNullableByteArray(buffer);
        byte[] glassesTextureData = readNullableByteArray(buffer);
        byte[] accessoryTextureData = readNullableByteArray(buffer);

        return new RegisterCustomHarnessPacket(
                harnessId, harnessName,
                saddleTextureData,
                glassesTextureData, accessoryTextureData
        );
    }

    public static void handle(RegisterCustomHarnessPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // проверяем, является ли это одиночкой
            if (!CustomHarnessManager.isSinglePlayerServer(player.getServer())) {
                LOGGER.info("Custom harness registration blocked in multiplayer from player {}",
                        player.getName().getString());

                // отправляем сообщение игроку
                player.sendSystemMessage(Component.literal(
                        "Custom harness creation is currently only available in single-player mode."));

                ctx.get().setPacketHandled(true);
                return;
            }

            try {
                // регистрируем кастомную сбрую
                boolean success = CustomHarnessManager.registerCustomHarness(
                        packet.harnessId,
                        packet.harnessName,
                        packet.saddleTextureData,
                        packet.glassesTextureData,
                        packet.accessoryTextureData,
                        player
                );

                if (success) {
                    // выдаем игроку предмет кастомной сбруи
                    CustomHarnessManager.giveCustomHarnessToPlayer(packet.harnessId, player);
                }
            } catch (Exception e) {
                LOGGER.error("Error processing custom harness registration from player {}: {}",
                        player.getName().getString(), e.getMessage());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // вспомогательные методы для nullable byte array
    private static void writeNullableByteArray(FriendlyByteBuf buffer, byte[] data) {
        if (data == null) {
            buffer.writeBoolean(false);
        } else {
            buffer.writeBoolean(true);
            buffer.writeByteArray(data);
        }
    }

    private static byte[] readNullableByteArray(FriendlyByteBuf buffer) {
        boolean hasData = buffer.readBoolean();
        if (hasData) {
            return buffer.readByteArray();
        } else {
            return null;
        }
    }
}