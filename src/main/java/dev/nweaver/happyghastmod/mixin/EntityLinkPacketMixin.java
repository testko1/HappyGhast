package dev.nweaver.happyghastmod.mixin; // убедитесь что пакет правильный

import dev.nweaver.happyghastmod.mixin.MobAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ClientboundSetEntityLinkPacket.class)
public class EntityLinkPacketMixin {

    @Shadow private int sourceId;
    @Shadow private int destId; // может быть 0 для отвязки

    @Inject(method = "handle(Lnet/minecraft/network/protocol/game/ClientGamePacketListener;)V", at = @At("TAIL"))
    private void hg_onHandleLinkPacket(net.minecraft.network.protocol.game.ClientGamePacketListener packetListener, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        Entity source = minecraft.level.getEntity(this.sourceId);
        Entity target = minecraft.level.getEntity(this.destId); // null, если destid == 0

        if (source instanceof Mob sourceMob) {
            ((MobAccessor) sourceMob).setLeashHolder(target);
        }
    }
}