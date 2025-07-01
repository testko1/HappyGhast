package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

// пакет для синхронизации данных кастомной сбруи с сервера на клиент
public class SyncCustomHarnessPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final String harnessId;
    private final String harnessName;
    private final String creatorName;
    private final byte[] saddleTextureData;
    private final byte[] glassesTextureData;
    private final byte[] accessoryTextureData;

    public SyncCustomHarnessPacket(String harnessId, String harnessName, String creatorName,
                                   byte[] saddleTextureData,
                                   byte[] glassesTextureData, byte[] accessoryTextureData) {
        this.harnessId = harnessId;
        this.harnessName = harnessName;
        this.creatorName = creatorName;
        this.saddleTextureData = saddleTextureData;
        this.glassesTextureData = glassesTextureData;
        this.accessoryTextureData = accessoryTextureData;
    }

    public static void encode(SyncCustomHarnessPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.harnessId);
        buffer.writeUtf(packet.harnessName);
        buffer.writeUtf(packet.creatorName);

        // записываем текстуры с проверкой на null
        writeNullableByteArray(buffer, packet.saddleTextureData);
        writeNullableByteArray(buffer, packet.glassesTextureData);
        writeNullableByteArray(buffer, packet.accessoryTextureData);
    }

    public static SyncCustomHarnessPacket decode(FriendlyByteBuf buffer) {
        String harnessId = buffer.readUtf();
        String harnessName = buffer.readUtf();
        String creatorName = buffer.readUtf();

        // читаем опциональные текстуры
        byte[] saddleTextureData = readNullableByteArray(buffer);
        byte[] glassesTextureData = readNullableByteArray(buffer);
        byte[] accessoryTextureData = readNullableByteArray(buffer);

        return new SyncCustomHarnessPacket(
                harnessId, harnessName, creatorName,
                saddleTextureData,
                glassesTextureData, accessoryTextureData
        );
    }

    public static void handle(SyncCustomHarnessPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // этот пакет идет с сервера на клиент, поэтому обрабатываем только на клиенте
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClientSide(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClientSide(SyncCustomHarnessPacket packet) {
        // регистрируем кастомную сбрую на клиенте
        ClientCustomHarnessManager.registerCustomHarness(
                packet.harnessId,
                packet.harnessName,
                packet.creatorName,
                packet.saddleTextureData,
                packet.glassesTextureData,
                packet.accessoryTextureData
        );
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