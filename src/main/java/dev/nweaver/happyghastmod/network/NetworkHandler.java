package dev.nweaver.happyghastmod.network;

import dev.nweaver.happyghastmod.HappyGhastMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Optional;

public class NetworkHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            HappyGhastMod.rl("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static int nextId() {
        return packetId++;
    }

    public static void init() {
        LOGGER.info("Initializing network packets");

        // общие пакеты движения (работают на сервере и клиенте)
        CHANNEL.registerMessage(
                nextId(),
                GhastVerticalMovementPacket.class,
                GhastVerticalMovementPacket::encode,
                GhastVerticalMovementPacket::decode,
                GhastVerticalMovementPacket::handle
        );
        CHANNEL.registerMessage(
                nextId(),
                OpenGhastInventoryPacket.class,
                OpenGhastInventoryPacket::encode,
                OpenGhastInventoryPacket::decode,
                OpenGhastInventoryPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // пакеты сервер -> клиент
        CHANNEL.registerMessage(
                nextId(),
                SyncCustomHarnessPacket.class,
                SyncCustomHarnessPacket::encode,
                SyncCustomHarnessPacket::decode,
                SyncCustomHarnessPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        // пакеты клиент -> сервер
        CHANNEL.registerMessage(
                nextId(),
                RegisterCustomHarnessPacket.class,
                RegisterCustomHarnessPacket::encode,
                RegisterCustomHarnessPacket::decode,
                RegisterCustomHarnessPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // пакеты сервер -> клиент
        CHANNEL.registerMessage(
                nextId(),
                OpenHarnessCreatorPacket.class,
                OpenHarnessCreatorPacket::encode,
                OpenHarnessCreatorPacket::decode,
                OpenHarnessCreatorPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextId(),
                GhastRotationSyncPacket.class,
                GhastRotationSyncPacket::encode,
                GhastRotationSyncPacket::decode,
                GhastRotationSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                nextId(),
                GhastSpeedSyncPacket.class,
                GhastSpeedSyncPacket::encode,
                GhastSpeedSyncPacket::decode,
                GhastSpeedSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT) // сервер отправляет клиентам
        );

        // регистрация нового пакета синхронизации квадро-поводка
        CHANNEL.registerMessage(
                nextId(),
                GhastQuadLeashSyncPacket.class,
                GhastQuadLeashSyncPacket::encode,
                GhastQuadLeashSyncPacket::decode,
                GhastQuadLeashSyncPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT) // сервер отправляет клиентам
        );

        // регистрация пакета расширенного взаимодействия с гастом (клиент->сервер)
        CHANNEL.registerMessage(
                nextId(),
                ExtendedGhastInteractionPacket.class,
                ExtendedGhastInteractionPacket::encode,
                ExtendedGhastInteractionPacket::decode,
                ExtendedGhastInteractionPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        // регистрируем обработчики на стороне клиента
        // это гарантирует что классы клиента не загружаются на сервере
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientPacketHandlers();
        }
    }

    // этот метод вызывается только на стороне клиента
    private static void registerClientPacketHandlers() {
        LOGGER.info("Registering client-side packet handlers");
    }

    // отправка пакетов на сервер
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    // отправка пакетов конкретному игроку
    public static void sendToPlayer(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    // отправка пакетов всем игрокам
    public static void sendToAll(Object packet) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }

}