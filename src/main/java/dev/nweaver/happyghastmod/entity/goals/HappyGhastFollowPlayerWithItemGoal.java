package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.AnchorManager;
import net.minecraft.world.phys.shapes.VoxelShape;


public class HappyGhastFollowPlayerWithItemGoal extends Goal {
    private final Ghast ghast;
    private Player player;
    private final double speedModifier;
    private final float startDistance;

    // Переменные для сглаживания движения
    private double smoothedTargetY = Double.NaN; // Сглаженная целевая высота
    private static final double VERTICAL_SMOOTHING = 0.08; // Коэффициент сглаживания (меньше = плавнее)
    private int verticalPositionMode = 0; // 0 - сверху, 1 - на уровне, 2 - снизу
    private int positionChangeTimer = 0;
    private static final int POSITION_CHANGE_INTERVAL = 200; // Интервал смены режима в тиках (10 секунд при 20 TPS)
    private static final float MIN_VERTICAL_OFFSET = 3.0F; // Минимальное вертикальное смещение
    private static final float[] VERTICAL_OFFSETS = {5.0F, 0.0F, -4.0F}; // Смещения для разных позиций
    private boolean forcePositionChange = true; // Флаг для принудительной смены позиции
    private static final double VERTICAL_MOVEMENT_RANGE = 30.0;
    // Отслеживание состояния предмета
    private boolean wasHoldingItem = false;

    public HappyGhastFollowPlayerWithItemGoal(Ghast ghast, double speedModifier, float startDistance) {
        this.ghast = ghast;
        this.speedModifier = speedModifier;
        this.startDistance = startDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // Проверяем, привязан ли гаст к забору
        if (this.ghast.isLeashed() && this.ghast.getLeashHolder() instanceof LeashFenceKnotEntity) {
            return false; // Не следуем за игроком если привязан к забору
        }

        // Проверяем, привязан ли гаст обычным поводком (не квадро)
        if (this.ghast.isLeashed() && !(this.ghast instanceof HappyGhast && ((HappyGhast)this.ghast).isQuadLeashing())) {
            return false;
        }

        this.player = this.ghast.level().getNearestPlayer(
                this.ghast, this.startDistance * 3);

        if (this.player == null) {
            if (wasHoldingItem) {
                wasHoldingItem = false;
                AnchorManager.stopFollowing(this.ghast);
            }
            return false;
        }

        // Проверяем, держит ли игрок снежок или сбрую в любой руке
        boolean holdingFollowItem = isHoldingFollowItem(this.player);

        // Отслеживаем изменение состояния предмета
        if (holdingFollowItem && !wasHoldingItem) {
            // Игрок только что взял предмет
            AnchorManager.markAsFollowing(this.ghast);
            AnchorManager.updateLastPlayerPosition(this.ghast, this.player.getX(), this.player.getY(), this.player.getZ());
        } else if (!holdingFollowItem && wasHoldingItem) {
            // Игрок только что убрал предмет - сразу останавливаем режим следования
            AnchorManager.stopFollowing(this.ghast);
        }

        // Запоминаем текущее состояние
        wasHoldingItem = holdingFollowItem;

        // Условие для активации цели следования
        boolean shouldFollow = holdingFollowItem && this.ghast.distanceToSqr(this.player) > 16.0D;

        // Если игрок держит предмет, но гаст не может следовать (слишком близко),
        // все равно обновляем состояние, но ограничиваем частоту обновления якоря
        if (holdingFollowItem && !shouldFollow) {
            if (AnchorManager.hasAnchor(this.ghast)) {
                AnchorManager.updateLastPlayerPosition(this.ghast,
                        this.player.getX(), this.player.getY(), this.player.getZ());

                // Если игрок очень далеко, создаем временный якорь, но с ограничением частоты
                double distanceSq = this.ghast.distanceToSqr(this.player);
                if (distanceSq > 256.0D && AnchorManager.canUpdateAnchor(this.ghast)) { // 16 блоков в квадрате
                    AnchorManager.createTemporaryAnchor(this.ghast,
                            this.player.getX(), this.player.getY(), this.player.getZ());
                }
            }
        }

        return shouldFollow;
    }

    @Override
    public boolean canContinueToUse() {
        // Проверяем привязку к забору
        if (this.ghast.isLeashed() && this.ghast.getLeashHolder() instanceof LeashFenceKnotEntity) {
            return false;
        }

        return isHoldingFollowItem(this.player) &&
                this.ghast.distanceToSqr(this.player) > 16.0D &&
                this.player.isAlive();
    }

    @Override
    public void stop() {
        this.player = null;
        if (wasHoldingItem) {
            wasHoldingItem = false;
            AnchorManager.stopFollowing(this.ghast);
        }
        // Сбрасываем сглаженную высоту
        smoothedTargetY = Double.NaN;
    }

    private boolean isHoldingFollowItem(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        return isSnowballOrHarness(mainHand) || isSnowballOrHarness(offHand);
    }

    private boolean isSnowballOrHarness(ItemStack stack) {
        // Проверка на снежок
        if (stack.is(Items.SNOWBALL)) {
            return true;
        }

        // Проверка на сбрую по ID предмета или тегам
        String itemId = stack.getItem().toString().toLowerCase();

        // Проверяем, содержит ли название предмета ключевые слова, указывающие на сбрую
        boolean isHarness = itemId.contains("harness") ||
                stack.hasTag() && stack.getTag().contains("HarnessColor");

        return isHarness;
    }

    /**
     * Проверяет, показывает ли игрок седло или снежок
     */
    private boolean isPlayerShowingFollowItem(Player player) {
        return isHoldingFollowItem(player);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        // Проверяем, держит ли игрок еще предмет
        boolean currentlyHoldingItem = isHoldingFollowItem(this.player);

        // Если игрок убрал предмет, немедленно прекращаем следование
        if (!currentlyHoldingItem && wasHoldingItem) {
            wasHoldingItem = false;
            AnchorManager.stopFollowing(this.ghast);
            return;
        }

        // Обновляем состояние
        wasHoldingItem = currentlyHoldingItem;

        // Обновляем метку времени следования
        if (currentlyHoldingItem) {
            AnchorManager.markAsFollowing(this.ghast);
            AnchorManager.updateLastPlayerPosition(this.ghast,
                    this.player.getX(), this.player.getY(), this.player.getZ());
        }

        // Всегда смотрим на игрока, когда следуем за ним
        double dx = this.player.getX() - this.ghast.getX();
        double dz = this.player.getZ() - this.ghast.getZ();
        this.ghast.setYRot(-((float)Mth.atan2(dx, dz)) * (180F / (float)Math.PI));
        this.ghast.yBodyRot = this.ghast.getYRot();

        // === УПРОЩЕННАЯ ЛОГИКА ВЫБОРА ВЕРТИКАЛЬНОЙ ПОЗИЦИИ ===
        positionChangeTimer++;
        if (positionChangeTimer >= POSITION_CHANGE_INTERVAL || forcePositionChange) {
            // Выбираем новый режим позиционирования, отличный от текущего
            int newMode;
            do {
                newMode = this.ghast.getRandom().nextInt(3); // 0, 1 или 2
            } while (newMode == verticalPositionMode && !forcePositionChange);

            verticalPositionMode = newMode;
            positionChangeTimer = 0;
            forcePositionChange = false;

            // Сбрасываем сглаженную высоту для быстрого перехода к новой позиции
            smoothedTargetY = Double.NaN;
        }

        // УПРОЩЕННОЕ ОПРЕДЕЛЕНИЕ ЦЕЛЕВОЙ ВЫСОТЫ
        // Не используем findGroundHeight() для вертикальных ограничений
        double targetY = this.player.getY() + VERTICAL_OFFSETS[verticalPositionMode];

        // Инициализация сглаженной высоты при первом использовании
        if (Double.isNaN(smoothedTargetY)) {
            smoothedTargetY = this.ghast.getY();
        }

        // УЛУЧШЕННАЯ ЛОГИКА ВЕРТИКАЛЬНОГО СГЛАЖИВАНИЯ
        double heightDiff = Math.abs(targetY - this.ghast.getY());
        double verticalSpeed;

        // ЗНАЧИТЕЛЬНОЕ УВЕЛИЧЕНИЕ СКОРОСТИ ДЛЯ ВЕРТИКАЛЬНОГО ДВИЖЕНИЯ
        if (heightDiff > 10.0D) {
            verticalSpeed = 0.3; // Увеличено с 0.2 - очень быстро при большой разнице
        } else if (heightDiff > 5.0D) {
            verticalSpeed = 0.2; // Увеличено с 0.1 - быстро при средней разнице
        } else {
            verticalSpeed = 0.1; // Увеличено с 0.05 - умеренно при малой разнице
        }

        // Сбалансированное движение вверх и вниз с небольшим преимуществом для спуска
        if (targetY < this.ghast.getY()) {
            verticalSpeed *= 1.15; // Небольшое преимущество для спуска - исправление бага остановки
        }

        // Применяем плавную интерполяцию к целевой высоте
        smoothedTargetY = smoothedTargetY + (targetY - smoothedTargetY) * verticalSpeed;

        // Обновляем идеальную высоту
        double idealHeight = smoothedTargetY;

        // === ЛОГИКА ДВИЖЕНИЯ К ИГРОКУ ===
        // Если гаст достаточно далеко - подлетаем ближе
        if (this.ghast.distanceToSqr(this.player) > 64.0D) {
            // Рассчитываем позицию недалеко от игрока
            double offsetX = this.player.getX() + (this.ghast.getRandom().nextFloat() * 8.0F - 4.0F);
            double offsetY = idealHeight; // Используем вычисленную выше высоту
            double offsetZ = this.player.getZ() + (this.ghast.getRandom().nextFloat() * 8.0F - 4.0F);

            // Когда показывается предмет, игнорируем проверку радиуса
            boolean canMove = currentlyHoldingItem ||
                    AnchorManager.isWithinAllowedRadius(this.ghast, offsetX, offsetY, offsetZ);

            if (canMove) {
                this.ghast.getMoveControl().setWantedPosition(offsetX, offsetY, offsetZ, this.speedModifier);
            } else if (AnchorManager.hasAnchor(this.ghast)) {
                // Движемся в пределах доступного радиуса
                Vec3 anchor = AnchorManager.getAnchor(this.ghast);
                double maxRadius = AnchorManager.getMaxRadius(this.ghast);

                Vec3 dirToPlayer = new Vec3(
                        this.player.getX() - anchor.x,
                        0,
                        this.player.getZ() - anchor.z
                ).normalize();

                double newX = anchor.x + dirToPlayer.x * maxRadius * 0.8;
                double newZ = anchor.z + dirToPlayer.z * maxRadius * 0.8;

                this.ghast.getMoveControl().setWantedPosition(newX, offsetY, newZ, this.speedModifier);
            }
        } else {
            // Если гаст достаточно близко, просто зависаем на расстоянии
            double dist = Math.sqrt(this.ghast.distanceToSqr(this.player));
            if (dist < 10.0D) {
                // Отступаем немного назад, если слишком близко
                Vec3 moveAwayVec = new Vec3(
                        this.ghast.getX() - this.player.getX(),
                        0,
                        this.ghast.getZ() - this.player.getZ()
                ).normalize();

                double newX = this.ghast.getX() + moveAwayVec.x * 2;
                double newY = idealHeight; // Используем вычисленную выше высоту
                double newZ = this.ghast.getZ() + moveAwayVec.z * 2;

                // Когда показывается предмет, игнорируем проверку радиуса
                boolean canMove = currentlyHoldingItem ||
                        AnchorManager.isWithinAllowedRadius(this.ghast, newX, newY, newZ);

                if (canMove) {
                    this.ghast.getMoveControl().setWantedPosition(newX, newY, newZ, 1.0D);
                }
            } else {
                // Просто зависаем на текущей позиции с новой целевой высотой
                this.ghast.getMoveControl().setWantedPosition(
                        this.ghast.getX(),
                        idealHeight,
                        this.ghast.getZ(),
                        0.5D);
            }
        }
    }

    /**
     * Находит высоту блока в указанной позиции X,Z
     * @return Y-координата верхней части блока или -1, если блок не найден
     */
    private int findGroundHeight(double x, double z) {
        Level level = this.ghast.level();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(this.ghast.getY()) - 1;
        // Увеличиваем глубину проверки для superflat миров
        int minY = Math.max(this.ghast.level().getMinBuildHeight(), startY - 256);

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