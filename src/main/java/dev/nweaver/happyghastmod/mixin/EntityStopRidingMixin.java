package dev.nweaver.happyghastmod.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat; // явный импорт
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityStopRidingMixin {

    // предотвращаем спешивание с лодок при привязанном поводке
    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void hg_onStopRiding(CallbackInfo ci) {
        Entity self = (Entity)(Object)this;

        // если это моб, он привязан (проверяем через accessor на всякий случай)
        // и сидит в лодке, отменяем спешивание
        if (self instanceof Mob mob &&
                ((MobAccessor)mob).getLeashHolderAccessor() != null && // проверяем факт привязки
                mob.isPassenger() &&
                mob.getVehicle() instanceof Boat) {
            ci.cancel();
        }
    }
}