package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.AnchorManager;
import dev.nweaver.happyghastmod.entity.Ghastling;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Улучшенная цель для Ghastling, чтобы следовать за игроком, держащим снежок.
 * Основана на HappyGhastFollowPlayerWithItemGoal с адаптацией для Ghastling.
 */
public class GhastlingFollowPlayerWithSnowballGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Ghast ghastling;
    private Player player;
    private final double speedModifier;
    private final float startDistance;

    // Переменные для сглаживания высоты
    private double smoothedTargetY = Double.NaN;
    private static final double VERTICAL_SMOOTHING = 0.1; // Более быстрая скорость для маленького гастлинга

    // Отслеживание состояния предмета
    private boolean wasHoldingSnowball = false;

    // Константа для высоты над игроком (меньше, чем у обычного гаста)
    private static final double HEIGHT_ABOVE_PLAYER = 1.5D;

    // Констаны для вариации высоты (меньше диапазон, чем у HappyGhast)
    private static final int POSITION_CHANGE_INTERVAL = 100; // 5 секунд при 20 TPS
    private int positionChangeTimer = 0;
    private int verticalPositionMode = 0; // 0 - сверху, 1 - на уровне
    private static final float[] VERTICAL_OFFSETS = {3.0F, 1.0F}; // Только два режима для Ghastling

    public GhastlingFollowPlayerWithSnowballGoal(Ghast ghastling, double speedModifier, float startDistance) {
        this.ghastling = ghastling;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Ищем ближайшего игрока в большем радиусе (x3), чтобы учесть возможность видеть игрока издалека
        this.player = this.ghastling.level().getNearestPlayer(
                this.ghastling, this.startDistance * 3);

        if (this.player == null) {
            if (wasHoldingSnowball) {
                wasHoldingSnowball = false;
                AnchorManager.stopFollowing(this.ghastling);
                LOGGER.debug("Ghastling stopped following - player not found");
            }
            return false;
        }

        // Проверяем, держит ли игрок снежок в любой руке
        boolean holdingSnowball = isHoldingSnowball(this.player);

        // Отслеживаем изменение состояния предмета
        if (holdingSnowball && !wasHoldingSnowball) {
            // Игрок только что взял снежок
            AnchorManager.markAsFollowing(this.ghastling);
            AnchorManager.updateLastPlayerPosition(this.ghastling, this.player.getX(), this.player.getY(), this.player.getZ());
            LOGGER.debug("Ghastling started following - player took out snowball");
        } else if (!holdingSnowball && wasHoldingSnowball) {
            // Игрок только что убрал снежок
            AnchorManager.stopFollowing(this.ghastling);
            LOGGER.debug("Ghastling stopped following - player put away snowball");
        }

        // Запоминаем текущее состояние
        wasHoldingSnowball = holdingSnowball;

        // Условие для активации цели
        boolean shouldFollow = holdingSnowball && this.ghastling.distanceToSqr(this.player) > 9.0D;

        // Даже если мы не выбираем эту цель (слишком близко), но игрок держит снежок,
        // все равно обновляем состояние AnchorManager
        if (holdingSnowball && !shouldFollow) {
            if (AnchorManager.hasAnchor(this.ghastling)) {
                AnchorManager.updateLastPlayerPosition(this.ghastling,
                        this.player.getX(), this.player.getY(), this.player.getZ());

                // Для больших расстояний перемещаем якорь
                double distanceSq = this.ghastling.distanceToSqr(this.player);
                if (distanceSq > 256.0D && AnchorManager.canUpdateAnchor(this.ghastling)) {
                    AnchorManager.createTemporaryAnchor(this.ghastling,
                            this.player.getX(), this.player.getY(), this.player.getZ());
                    LOGGER.debug("Created temporary anchor for Ghastling");
                }
            }
        }

        return shouldFollow;
    }

    @Override
    public boolean canContinueToUse() {
        return isHoldingSnowball(this.player) &&
                this.ghastling.distanceToSqr(this.player) > 9.0D &&
                this.player.isAlive();
    }

    private boolean isHoldingSnowball(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        return mainHand.is(Items.SNOWBALL) || offHand.is(Items.SNOWBALL);
    }

    @Override
    public void stop() {
        this.player = null;
        if (wasHoldingSnowball) {
            wasHoldingSnowball = false;
            AnchorManager.stopFollowing(this.ghastling);
            LOGGER.debug("Ghastling stopped following - goal stopped");
        }
        // Сбрасываем сглаженную высоту
        smoothedTargetY = Double.NaN;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Проверяем, держит ли игрок еще снежок
        boolean currentlyHoldingSnowball = isHoldingSnowball(this.player);

        // Если игрок убрал снежок, немедленно прекращаем следование
        if (!currentlyHoldingSnowball && wasHoldingSnowball) {
            wasHoldingSnowball = false;
            AnchorManager.stopFollowing(this.ghastling);
            LOGGER.debug("Ghastling stopped following - player put away snowball during tick");
            return;
        }

        // Обновляем состояние
        wasHoldingSnowball = currentlyHoldingSnowball;

        // Обновляем метку времени следования
        if (currentlyHoldingSnowball) {
            AnchorManager.markAsFollowing(this.ghastling);
            AnchorManager.updateLastPlayerPosition(this.ghastling,
                    this.player.getX(), this.player.getY(), this.player.getZ());
        }

        // ВАЖНО: Всегда смотрим на игрока, когда следуем за ним -
        // этот код поднят выше, чтобы гарантировать приоритет взгляда на игрока
        double dx = this.player.getX() - this.ghastling.getX();
        double dz = this.player.getZ() - this.ghastling.getZ();
        float targetYaw = -((float)Mth.atan2(dx, dz)) * (180F / (float)Math.PI);

        // Форсируем поворот напрямую, чтобы гарантировать, что гастлинг смотрит на игрока
        this.ghastling.setYRot(targetYaw);
        this.ghastling.yBodyRot = this.ghastling.getYRot();

        // Обновляем также значение в LookGoal, чтобы избежать "подергиваний"
        if (this.ghastling.goalSelector.getRunningGoals()
                .anyMatch(goal -> goal.getGoal() instanceof GhastlingLookGoal)) {
            try {
                // Рефлексия может не работать, поэтому используем try-catch
                for (Goal.Flag flag : Goal.Flag.values()) {
                    this.ghastling.goalSelector.disableControlFlag(flag);
                }
                // Включаем только флаг MOVE, чтобы LookGoal не перезаписал наш поворот
                this.ghastling.goalSelector.enableControlFlag(Goal.Flag.MOVE);
            } catch (Exception e) {
                LOGGER.debug("Failed to disable goal flags: {}", e.getMessage());
            }
        }

        // Инкрементируем таймер смены позиции
        positionChangeTimer++;

        // Проверяем, нужно ли изменить позицию
        if (positionChangeTimer >= POSITION_CHANGE_INTERVAL) {
            // Выбираем новый режим позиционирования
            verticalPositionMode = this.ghastling.getRandom().nextInt(2); // 0 или 1
            positionChangeTimer = 0;
            LOGGER.debug("Ghastling position changed to mode: {}", verticalPositionMode);
            // Сбрасываем сглаженную высоту для быстрого перехода к новой позиции
            smoothedTargetY = Double.NaN;
        }

        // Определяем целевую высоту
        double targetY;

        // Проверяем наличие земли под игроком и под гастлингом
        int groundUnderPlayer = findGroundHeight(this.player.getX(), this.player.getZ());
        int groundUnderGhastling = findGroundHeight(this.ghastling.getX(), this.ghastling.getZ());

        // Выбираем наиболее подходящую высоту земли
        int groundY = groundUnderPlayer != -1 ? groundUnderPlayer : groundUnderGhastling;

        if (groundY != -1) {
            // Если нашли землю, устанавливаем высоту относительно неё
            // Гастлинг держится ниже и ближе к земле, чем HappyGhast
            double baseHeight = groundY + 1.0;

            // Применяем режим позиционирования (меньше вариаций для гастлинга)
            if (verticalPositionMode == 0) { // Сверху
                targetY = baseHeight + 1.0;
            } else { // На уровне
                targetY = baseHeight + 0.0;
            }

            // Предотвращаем слишком высокий полет
            double maxHeightAboveGround = groundY + 3.0;
            targetY = Math.min(targetY, maxHeightAboveGround);
        } else {
            // Если земли не нашли, используем позицию игрока, но с ограничением
            targetY = this.player.getY() + (verticalPositionMode == 0 ? 2.0F : 0.5F);

            // Ограничиваем максимальную абсолютную высоту
            int minBuildHeight = this.ghastling.level().getMinBuildHeight();
            int maxHeightAboveVoid = minBuildHeight + 25; // Ghastling летает ниже, чем HappyGhast

            if (targetY > maxHeightAboveVoid) {
                targetY = maxHeightAboveVoid;
            }
        }

        // Убедимся, что высота не меньше минимальной безопасной высоты
        double minSafeHeight = this.ghastling.level().getMinBuildHeight() + 3;
        if (targetY < minSafeHeight) {
            targetY = minSafeHeight;
        }

        // Инициализация сглаженной высоты при первом использовании
        if (Double.isNaN(smoothedTargetY)) {
            smoothedTargetY = this.ghastling.getY();
        }

        // Определяем скорость изменения высоты в зависимости от разницы
        double heightDiff = Math.abs(targetY - this.ghastling.getY());
        double verticalSpeed;

        if (heightDiff > 8.0D) {
            verticalSpeed = 0.2; // Очень быстро при большой разнице
        } else if (heightDiff > 4.0D) {
            verticalSpeed = 0.1; // Быстро при средней разнице
        } else {
            verticalSpeed = 0.05; // Умеренно при малой разнице
        }

        // Применяем плавную интерполяцию к целевой высоте
        smoothedTargetY = smoothedTargetY + (targetY - smoothedTargetY) * verticalSpeed;

        // Если гастлинг достаточно далеко от игрока, подлетаем ближе
        if (this.ghastling.distanceToSqr(this.player) > 36.0D) {
            // Рассчитываем позицию недалеко от игрока
            double offsetX = this.player.getX() + (this.ghastling.getRandom().nextFloat() * 4.0F - 2.0F);
            double offsetY = smoothedTargetY; // Используем сглаженную высоту
            double offsetZ = this.player.getZ() + (this.ghastling.getRandom().nextFloat() * 4.0F - 2.0F);

            this.ghastling.getMoveControl().setWantedPosition(offsetX, offsetY, offsetZ, this.speedModifier);
        } else {
            // Если гастлинг достаточно близко, просто зависаем на расстоянии
            double dist = Math.sqrt(this.ghastling.distanceToSqr(this.player));
            if (dist < 4.0D) {
                // Отступаем немного назад, если слишком близко
                Vec3 moveAwayVec = new Vec3(
                        this.ghastling.getX() - this.player.getX(),
                        0,
                        this.ghastling.getZ() - this.player.getZ()
                ).normalize();

                this.ghastling.getMoveControl().setWantedPosition(
                        this.ghastling.getX() + moveAwayVec.x * 1.5,
                        smoothedTargetY,
                        this.ghastling.getZ() + moveAwayVec.z * 1.5,
                        0.8D);
            } else {
                // Просто зависаем на текущей позиции с новой высотой
                this.ghastling.getMoveControl().setWantedPosition(
                        this.ghastling.getX(),
                        smoothedTargetY,
                        this.ghastling.getZ(),
                        0.4D);
            }
        }
    }

    /**
     * Находит высоту блока в указанной позиции X,Z
     * @return Y-координата верхней части блока или -1, если блок не найден
     */
    private int findGroundHeight(double x, double z) {
        Level level = this.ghastling.level();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(this.ghastling.getY()) - 1;
        // Проверяем на большую глубину - до 128 блоков вниз
        int minY = Math.max(this.ghastling.level().getMinBuildHeight(), startY - 128);

        // Проверяем блоки сверху вниз
        for (int y = startY; y >= minY; y--) {
            BlockPos blockPos = new BlockPos(blockX, y, blockZ);
            BlockState blockState = level.getBlockState(blockPos);

            if (!blockState.isAir() && !blockState.liquid()) {
                VoxelShape collisionShape = blockState.getCollisionShape(level, blockPos);
                if (!collisionShape.isEmpty()) {
                    return y + 1;
                }
            }
        }

        return -1;
    }

}