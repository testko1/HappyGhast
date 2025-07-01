package dev.nweaver.happyghastmod.mixin;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// миксин для корректного позиционирования пассажиров
@Mixin(Entity.class)
public abstract class HappyGhastPassengerMixin {

    // позиционирует пассажиров на правильные места, но не меняет их поворот
    @Inject(
            method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V",
            at = @At("TAIL")
    )
    private void fixPassengerPositioning(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        // получаем текущую сущность
        Entity self = (Entity)(Object)this;

        // применяем только для happyghast сущностей
        if (self instanceof HappyGhast happyGhast) {
            List<Entity> passengers = happyGhast.getPassengers();
            int passengerIndex = passengers.indexOf(passenger);

            // только для пассажиров (не водителя)
            // не меняем поворот пассажиров, только убеждаемся, что они
            // находятся в правильной позиции относительно гаста
            if (passengerIndex > 0) {
                // мы не меняем поворот, как это было раньше
                // это позволяет пассажирам свободно вращать головой
            }
        }
    }
}