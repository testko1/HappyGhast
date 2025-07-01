package dev.nweaver.happyghastmod.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// миксин для поворота привязанных сущностей лицом к держателю поводка
@Mixin(Mob.class)
public abstract class MobOrientationMixin {

    // поворачиваем моба только когда он действительно тянется на поводке
    private static final double LEASH_TENSION_DISTANCE = 8.0;
    private static final double LEASH_TENSION_DISTANCE_SQ = LEASH_TENSION_DISTANCE * LEASH_TENSION_DISTANCE;

    // счетчик тиков для обновления ориентации
    private int orientationUpdateTicks = 0;

    // затеняем методы для доступа к внутренним переменным mob
    @Shadow public abstract boolean isLeashed();
    @Shadow public abstract Entity getLeashHolder();

    // инъекция в метод basetick для обновления ориентации привязанной сущности
    // теперь обновляем поворот только каждые 4 тиков и только при движении моба
    @Inject(method = "baseTick", at = @At("TAIL"))
    private void hg_updateLeashOrientation(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;

        // обновляем ориентацию только если есть поводок
        if (!mob.isLeashed()) {
            return;
        }

        // получаем держателя поводка
        Entity holder = mob.getLeashHolder();
        if (holder == null) {
            return;
        }

        // используем центры bounding box
        Vec3 mobPos = mob.getBoundingBox().getCenter();
        Vec3 holderPos = holder.getBoundingBox().getCenter();

        // вычисляем горизонтальное расстояние (без учета высоты)
        double dx = holderPos.x - mobPos.x;
        double dz = holderPos.z - mobPos.z;
        double horizontalDistSq = dx * dx + dz * dz;

        // поворачиваем моба только если расстояние достаточно большое
        // и только каждые 4 тика для уменьшения дерганий
        if (horizontalDistSq > LEASH_TENSION_DISTANCE_SQ && ++orientationUpdateTicks >= 4) {
            orientationUpdateTicks = 0;

            // вычисляем целевой угол поворота
            float targetYaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            targetYaw = Mth.wrapDegrees(targetYaw);

            // получаем текущий угол
            float currentYaw = Mth.wrapDegrees(mob.getYRot());

            // вычисляем разницу углов
            float deltaYaw = Mth.wrapDegrees(targetYaw - currentYaw);

            // ограничиваем скорость поворота и применяем сглаживание
            float maxRotation = 3.0F;
            if (Math.abs(deltaYaw) > maxRotation) {
                deltaYaw = Math.signum(deltaYaw) * maxRotation;
            }

            // применяем новый угол
            mob.setYRot(currentYaw + deltaYaw);

            // обновляем поворот головы и тела для единообразия
            // используем тот же угол, чтобы избежать дополнительных колебаний
            mob.yHeadRot = mob.getYRot();
            mob.yBodyRot = mob.getYRot();
        }
    }
}