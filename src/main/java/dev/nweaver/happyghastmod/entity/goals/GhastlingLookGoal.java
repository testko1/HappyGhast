package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.entity.AnchorManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Улучшенная цель для поворота Ghastling с разным поведением в зависимости от состояния привязи
 * Основана на HappyGhastLookGoal
 */
public class GhastlingLookGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Ghastling ghastling;

    // Переменные для сглаживания поворота
    private float lastYRot = 0.0F;
    private float targetYRot = 0.0F;

    // Сглаживающие векторы для более плавного поворота (только с привязью)
    private Vec3 smoothedDirection = null;
    private static final double DIR_SMOOTHING_LEASHED = 0.08; // Чуть быстрее чем у HappyGhast

    // Таймер поворота для привязанного режима
    private int rotationHoldTimer = 0;
    private static final int MIN_ROTATION_HOLD_TICKS = 8; // Меньше чем у HappyGhast для лучшей отзывчивости

    // Минимальная скорость для изменения направления для привязанного режима
    private static final double MIN_VELOCITY_FOR_ROTATION = 0.02;

    // Максимальная скорость поворота в градусах за тик для привязанного режима
    private static final float MAX_ROTATION_SPEED_LEASHED = 2.0F; // Немного быстрее чем у HappyGhast

    public GhastlingLookGoal(Ghastling ghastling) {
        this.ghastling = ghastling;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        this.lastYRot = ghastling.getYRot();
        this.targetYRot = ghastling.getYRot();
    }

    @Override
    public boolean canUse() {
        return true; // Всегда активна
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true; // Нужно обновлять каждый тик
    }

    @Override
    public void tick() {
        // Проверяем, привязан ли гастлинг
        boolean isLeashed = this.ghastling.isLeashed();
        Entity leashHolder = null;

        if (isLeashed) {
            leashHolder = this.ghastling.getLeashHolder();
        }

        // Проверяем, следует ли гастлинг за игроком
        boolean isFollowing = AnchorManager.isFollowing(this.ghastling);
        if (isFollowing) {
            return;
        }

        // Выбираем алгоритм поворота
        if (isLeashed && leashHolder != null) {
            // Плавный поворот на поводке
            tickLeashed(leashHolder);
        } else if (isFollowing) {
            // Поворот при следовании обрабатывается в FollowPlayerGoal
            // Просто сохраняем текущие углы
            this.lastYRot = this.ghastling.getYRot();
            this.targetYRot = this.ghastling.getYRot();
        } else {
            // Обычный поворот по направлению движения
            tickNormal();
            // Сбрасываем переменные сглаживания, если не привязан
            smoothedDirection = null;
            rotationHoldTimer = 0;
        }
    }

    /**
     * Обычное поведение для непривязанного гастлинга - поворот в сторону движения
     */
    private void tickNormal() {
        Vec3 delta = this.ghastling.getDeltaMovement();
        if (delta.horizontalDistanceSqr() > 1.0E-6D) { // Если есть горизонтальное движение
            // Рассчитываем угол напрямую по вектору скорости
            this.targetYRot = -((float)Mth.atan2(delta.x, delta.z)) * Mth.RAD_TO_DEG;
        }

        // Плавно интерполируем к целевому углу
        float rotationDiff = Mth.wrapDegrees(this.targetYRot - this.lastYRot);
        float rotationStep = Mth.clamp(rotationDiff, -10.0F, 10.0F);
        this.lastYRot = Mth.wrapDegrees(this.lastYRot + rotationStep);

        // Применяем угол
        this.ghastling.setYRot(this.lastYRot);
        this.ghastling.yBodyRot = this.ghastling.getYRot();
    }

    /**
     * Сглаженное поведение для привязанного гастлинга
     */
    private void tickLeashed(Entity leashHolder) {
        Vec3 motionVector = this.ghastling.getDeltaMovement();
        double horizontalSpeed = motionVector.horizontalDistance();

        boolean shouldUpdateDirection = horizontalSpeed > MIN_VELOCITY_FOR_ROTATION || rotationHoldTimer <= 0;

        if (shouldUpdateDirection) {
            rotationHoldTimer = MIN_ROTATION_HOLD_TICKS;

            // Вектор направления - смесь движения и направления к держателю
            Vec3 directionVector;
            Vec3 toLeashHolder = leashHolder.position().subtract(this.ghastling.position());

            if (horizontalSpeed > 0.01) {
                // Взвешенное среднее: 70% движение, 30% к держателю
                directionVector = motionVector.normalize().scale(0.7).add(toLeashHolder.normalize().scale(0.3));
            } else {
                // Если почти не движемся, смотрим на держателя
                directionVector = toLeashHolder;
            }

            // Сглаживаем направление
            if (directionVector.horizontalDistanceSqr() > 1.0E-6) {
                directionVector = directionVector.normalize();
                if (smoothedDirection == null) {
                    smoothedDirection = directionVector;
                } else {
                    smoothedDirection = smoothedDirection.scale(1 - DIR_SMOOTHING_LEASHED)
                            .add(directionVector.scale(DIR_SMOOTHING_LEASHED)).normalize();
                }
                // Обновляем целевой угол
                this.targetYRot = -((float)Mth.atan2(smoothedDirection.x, smoothedDirection.z)) * Mth.RAD_TO_DEG;
            }
        } else {
            rotationHoldTimer--; // Уменьшаем таймер
        }

        // Плавно поворачиваем к целевому углу с ограниченной скоростью
        float rotationDiff = Mth.wrapDegrees(this.targetYRot - this.lastYRot);
        float rotationStep = Mth.clamp(rotationDiff, -MAX_ROTATION_SPEED_LEASHED, MAX_ROTATION_SPEED_LEASHED);
        this.lastYRot = Mth.wrapDegrees(this.lastYRot + rotationStep);

        // Устанавливаем новый угол поворота
        this.ghastling.setYRot(this.lastYRot);
        this.ghastling.yBodyRot = this.ghastling.getYRot();
    }
}