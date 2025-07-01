package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

// пакет для синхронизации данных квадро-поводка между сервером и клиентом
public class GhastQuadLeashSyncPacket {
    private final int ghastId;
    private final CompoundTag leashedEntitiesData;
    private final boolean isQuadLeashing;

    public GhastQuadLeashSyncPacket(int ghastId, CompoundTag leashedEntitiesData, boolean isQuadLeashing) {
        this.ghastId = ghastId;
        this.leashedEntitiesData = leashedEntitiesData;
        this.isQuadLeashing = isQuadLeashing;
    }

    // записывает данные пакета в сетевой буфер
    public static void encode(GhastQuadLeashSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.ghastId);
        buf.writeNbt(packet.leashedEntitiesData);
        buf.writeBoolean(packet.isQuadLeashing);
    }

    // читает данные пакета из сетевого буфера
    public static GhastQuadLeashSyncPacket decode(FriendlyByteBuf buf) {
        int ghastId = buf.readInt();
        CompoundTag data = buf.readNbt();
        boolean isLeashing = buf.readBoolean();
        return new GhastQuadLeashSyncPacket(ghastId, data, isLeashing);
    }

    // обрабатывает пакет на принимающей стороне - безопасно ограничено клиентом
    public static void handle(GhastQuadLeashSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // используем DistExecutor, чтобы убедиться что это выполняется только на клиенте
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleOnClient(packet));
        });
        ctx.get().setPacketHandled(true);
    }

    // обработка пакета только на стороне клиента
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(GhastQuadLeashSyncPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            Entity entity = minecraft.level.getEntity(packet.ghastId);
            if (entity instanceof HappyGhast ghast) {
                // удаляем временную метку синхронизации, чтобы оставить только фактические данные
                CompoundTag cleanData = packet.leashedEntitiesData.copy();
                if (cleanData.contains("syncTimestamp")) {
                    cleanData.remove("syncTimestamp");
                }

                // обновляем данные в безопасном порядке
                // сначала обновляем состояние квадро-привязки
                ghast.setEntityDataValue(HappyGhast.DATA_IS_QUAD_LEASHING, packet.isQuadLeashing);

                // затем обновляем данные сущности
                ghast.setEntityDataValue(HappyGhast.DATA_QUAD_LEASHED_ENTITIES, cleanData);

            }
        }
    }
}