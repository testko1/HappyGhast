package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// миксин для синхронизации поворота гаста между клиентами
// для пассажиров корректирует только позицию, но не поворот
@Mixin(ServerGamePacketListenerImpl.class)
public class HappyGhastRotationMixin {

    @Shadow
    public ServerPlayer player;

    // используем threadlocal для отслеживания рекурсии
    private static final ThreadLocal<Boolean> IS_PROCESSING = ThreadLocal.withInitial(() -> false);

    // перехватываем пакеты перед отправкой клиенту
    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifyRotationPackets(Packet<?> packet, PacketSendListener packetSendListener, CallbackInfo ci) {
        // проверяем, не находимся ли мы уже внутри обработки пакета
        if (IS_PROCESSING.get()) {
            return;
        }

        try {
            IS_PROCESSING.set(true); // устанавливаем флаг обработки

            // обрабатываем только пакеты движения сущностей с поворотом
            if (packet instanceof ClientboundMoveEntityPacket movePacket && movePacket.hasRotation()) {
                Entity entity = movePacket.getEntity(this.player.level());

                // проверяем, что это пакет для entity, который является пассажиром гаста
                if (entity != null && entity.getVehicle() instanceof HappyGhast happyGhast) {
                    List<Entity> passengers = happyGhast.getPassengers();
                    int passengerIndex = passengers.indexOf(entity);

                    // для всех пассажиров кроме водителя (индекс 0), модифицируем пакет
                    if (passengerIndex > 0) {
                        // не отменяем пакет целиком, а модифицируем его вместо этого

                        // отправляем пакет как есть, если он содержит только позицию
                        if (movePacket.hasPosition() && !movePacket.hasRotation()) {
                            return; // пропускаем стандартную обработку без отмены
                        }

                        // если пакет содержит и позицию, и поворот - отправляем только позицию
                        if (movePacket.hasPosition() && movePacket.hasRotation()) {
                            // отменяем стандартную обработку пакета
                            ci.cancel();

                            // отправляем пакет только с позицией
                            ClientboundMoveEntityPacket.Pos newPacket = new ClientboundMoveEntityPacket.Pos(
                                    entity.getId(),
                                    movePacket.getXa(), movePacket.getYa(), movePacket.getZa(),
                                    movePacket.isOnGround()
                            );
                            this.player.connection.send(newPacket);
                        }
                    }
                }
            }
        } finally {
            IS_PROCESSING.set(false); // всегда сбрасываем флаг обработки
        }
    }
}