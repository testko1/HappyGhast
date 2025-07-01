package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// миксин для предотвращения управления гаста пассажирами, которые не водители
@Mixin(ServerGamePacketListenerImpl.class)
public class HappyGhastPassengerSyncMixin {

    @Shadow
    public ServerPlayer player;

    // перехватывает пакеты движения игрока и применяет их к гасту, только если игрок
    // является основным наездником (первый пассажир)
    @Inject(
            method = "handleMovePlayer",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onPlayerMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        // проверяем, сидит ли игрок на гасте
        if (this.player.getVehicle() instanceof HappyGhast happyGhast) {
            // получаем индекс игрока в списке пассажиров
            int playerIndex = happyGhast.getPassengers().indexOf(this.player);

            // если игрок не первый пассажир (не водитель), и пытается поворачивать гаста
            if (playerIndex > 0 && packet.hasRotation()) {
                // не отменяем пакеты поворота, а просто игнорируем их влияние на гаста
                // это позволит клиенту получать обновления поворота от сервера
                return; // возвращаемся без отмены
            }
        }
    }
}