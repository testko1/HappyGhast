package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.HappyGhast;

/**
 * Улучшенная цель для поворота гаста с разным поведением в зависимости от состояния привязи
 */
public class HappyGhastLookGoal extends Goal {
    private final Ghast ghast;

    // Переменные для сглаживания поворота
    private float lastYRot = 0.0F;
    private float targetYRot = 0.0F;

    // Сглаживающие векторы для более плавного поворота (только с привязью)
    private Vec3 smoothedDirection = null;
    private static final double DIR_SMOOTHING_LEASHED = 0.05;

    // Таймер поворота для привязанного режима
    private int rotationHoldTimer = 0;
    private static final int MIN_ROTATION_HOLD_TICKS = 10;

    // Минимальная скорость для изменения направления для привязанного режима
    private static final double MIN_VELOCITY_FOR_ROTATION = 0.02;

    // Максимальная скорость поворота в градусах за тик для привязанного режима
    private static final float MAX_ROTATION_SPEED_LEASHED = 1.5F;

    public HappyGhastLookGoal(Ghast ghast) {
        this.ghast = ghast;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        this.lastYRot = ghast.getYRot();
        this.targetYRot = ghast.getYRot();
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Проверяем, привязан ли гаст
        boolean isLeashed = false;
        Entity leashHolder = null;

        if (this.ghast instanceof HappyGhast happyGhast) {
            isLeashed = happyGhast.isLeashed();
            if (isLeashed) {
                leashHolder = happyGhast.getLeashHolder();
            }
        }

        // РАЗНЫЕ АЛГОРИТМЫ В ЗАВИСИМОСТИ ОТ ПРИВЯЗИ
        if (isLeashed && leashHolder != null) {
            // СГЛАЖЕННЫЙ АЛГОРИТМ ДЛЯ ПРИВЯЗАННОГО ГАСТА
            tickLeashed(leashHolder);
        } else {
            // СТАНДАРТНЫЙ АЛГОРИТМ ДЛЯ ОБЫЧНОГО ГАСТА
            tickNormal();

            // Сбрасываем переменные сглаживания, когда гаст не привязан
            smoothedDirection = null;
            rotationHoldTimer = 0;
        }
    }

    /**
     * Обычное поведение для непривязанного гаста - прямой поворот в сторону движения
     */
    private void tickNormal() {
        Vec3 vec3 = this.ghast.getDeltaMovement();

        // Только если есть значимое движение, меняем угол поворота
        if (vec3.horizontalDistanceSqr() > 0.0001D) {
            float newYRot = -((float)Mth.atan2(vec3.x, vec3.z)) * (180F / (float)Math.PI);

            // Прямая установка угла без сглаживания
            this.ghast.setYRot(newYRot);
            this.ghast.yBodyRot = this.ghast.getYRot();

            // Обновляем последний угол для случая, если гаст будет привязан
            this.lastYRot = newYRot;
            this.targetYRot = newYRot;
        }
    }

    /**
     * Сглаженное поведение для привязанного гаста
     */
    private void tickLeashed(Entity leashHolder) {
        // Получаем текущий вектор движения
        Vec3 motionVector = this.ghast.getDeltaMovement();
        double horizontalSpeed = motionVector.horizontalDistance();

        // Если скорость достаточна для обновления направления или таймер истек
        boolean shouldUpdateDirection = horizontalSpeed > MIN_VELOCITY_FOR_ROTATION || rotationHoldTimer <= 0;

        if (shouldUpdateDirection) {
            // Сбрасываем таймер
            rotationHoldTimer = MIN_ROTATION_HOLD_TICKS;

            // Создаем взвешенное среднее между вектором движения и вектором к точке привязи
            Vec3 directionVector = motionVector;

            if (horizontalSpeed > 0.01) {
                Vec3 toLeashHolder = leashHolder.position().subtract(this.ghast.position()).normalize();
                // Даем большее значение вектору движения для естественности
                directionVector = motionVector.normalize().scale(0.7).add(toLeashHolder.scale(0.3));
            }

            // Сглаживаем направление для уменьшения резких изменений
            if (smoothedDirection == null) {
                smoothedDirection = directionVector;
            } else if (directionVector.horizontalDistanceSqr() > 0.0001) {
                // Плавно обновляем направление через экспоненциальную скользящую среднюю
                smoothedDirection = smoothedDirection.scale(1 - DIR_SMOOTHING_LEASHED)
                        .add(directionVector.scale(DIR_SMOOTHING_LEASHED));
            }

            // Обновляем целевой угол поворота только если сглаженное направление значимо
            if (smoothedDirection.horizontalDistanceSqr() > 0.0001) {
                float newTargetYRot = -((float)Mth.atan2(smoothedDirection.x, smoothedDirection.z)) * (180F / (float)Math.PI);

                // Если разница между текущим и новым целевым углом значительна,
                // установим новый целевой угол, иначе оставим текущий для стабильности
                float rotDiff = Mth.wrapDegrees(newTargetYRot - targetYRot);
                if (Math.abs(rotDiff) > 5.0F) {
                    targetYRot = newTargetYRot;
                }
            }
        } else {
            // Уменьшаем таймер
            rotationHoldTimer--;
        }

        // Плавно поворачиваем к целевому углу с ограниченной скоростью
        float rotationDiff = Mth.wrapDegrees(targetYRot - lastYRot);

        // Ограничиваем скорость поворота для плавности
        float rotationStep = Mth.clamp(rotationDiff, -MAX_ROTATION_SPEED_LEASHED, MAX_ROTATION_SPEED_LEASHED);

        // Обновляем текущий угол поворота
        lastYRot = Mth.wrapDegrees(lastYRot + rotationStep);

        // Устанавливаем новый угол поворота для гаста
        this.ghast.setYRot(lastYRot);
        this.ghast.yBodyRot = this.ghast.getYRot();
    }
}