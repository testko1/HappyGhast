package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

// пакет для синхронизации поворота гаста между клиентами
public class GhastRotationSyncPacket {
    private static final Logger LOGGER = LogManager.getLogger();

    private final int entityId;
    private final float yRot;
    private final float xRot;
    private final float yBodyRot;
    private final float yHeadRot;

    public GhastRotationSyncPacket(int entityId, float yRot, float xRot, float yBodyRot, float yHeadRot) {
        this.entityId = entityId;
        this.yRot = yRot;
        this.xRot = xRot;
        this.yBodyRot = yBodyRot;
        this.yHeadRot = yHeadRot;
    }

    // кодирует пакет в буфер
    public static void encode(GhastRotationSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.entityId);
        buffer.writeFloat(packet.yRot);
        buffer.writeFloat(packet.xRot);
        buffer.writeFloat(packet.yBodyRot);
        buffer.writeFloat(packet.yHeadRot);
    }

    // декодирует пакет из буфера
    public static GhastRotationSyncPacket decode(FriendlyByteBuf buffer) {
        return new GhastRotationSyncPacket(
                buffer.readInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    // обрабатывает пакет
    public static void handle(GhastRotationSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // выполняем только на клиенте
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleOnClient(packet);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // обработка пакета на стороне клиента
    public static void handleOnClient(GhastRotationSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity entity = minecraft.level.getEntity(packet.entityId);
        if (!(entity instanceof HappyGhast ghast)) return;

        // проверяем, не управляет ли текущий игрок этим гастом
        boolean isDriver = false;
        if (minecraft.player != null && minecraft.player.getVehicle() == ghast) {
            int passengerIndex = ghast.getPassengers().indexOf(minecraft.player);
            isDriver = (passengerIndex == 0);
        }

        // водитель полностью игнорирует обновления с сервера
        if (isDriver) {
            return;
        }

        // для зрителей: сохраняем серверные значения как целевые для плавной интерполяции
        ghast.setTargetServerRotation(
                packet.yRot,
                packet.xRot,
                packet.yBodyRot,
                packet.yHeadRot
        );
    }
}