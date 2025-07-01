package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.AnchorManager;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class HappyGhastRandomFloatGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Ghast ghast;
    private static final double VERTICAL_MOVEMENT_RANGE = 30.0;

    public HappyGhastRandomFloatGoal(Ghast ghast) {
        this.ghast = ghast;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Если гаст следует за игроком, не используем случайное движение
        if (AnchorManager.isFollowing(this.ghast)) {
            return false;
        }

        // Проверяем, привязан ли гаст к забору
        if (this.ghast.isLeashed() && this.ghast.getLeashHolder() instanceof LeashFenceKnotEntity) {
            return false; // Не используем AI если привязан к забору
        }

        // Проверяем, привязан ли гаст обычным поводком (не квадро)
        if (this.ghast.isLeashed() && !(this.ghast instanceof HappyGhast && ((HappyGhast)this.ghast).isQuadLeashing())) {
            return false;
        }

        MoveControl movecontrol = this.ghast.getMoveControl();
        if (!movecontrol.hasWanted()) {
            return true;
        } else {
            double d0 = movecontrol.getWantedX() - this.ghast.getX();
            double d1 = movecontrol.getWantedY() - this.ghast.getY();
            double d2 = movecontrol.getWantedZ() - this.ghast.getZ();
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            return d3 < 1.0D || d3 > 3600.0D;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }

    @Override
    public void start() {
        RandomSource randomsource = this.ghast.getRandom();

        // Добавляем код для получения начальной высоты спавна из HappyGhast
        double spawnHeight = 0;
        boolean hasInitializedHeight = false;

        if (this.ghast instanceof HappyGhast) {
            HappyGhast happyGhast = (HappyGhast) this.ghast;
            // Получаем доступ к полям через рефлексию
            try {
                java.lang.reflect.Field spawnHeightField = HappyGhast.class.getDeclaredField("spawnHeight");
                java.lang.reflect.Field initializedField = HappyGhast.class.getDeclaredField("hasInitializedSpawnHeight");

                spawnHeightField.setAccessible(true);
                initializedField.setAccessible(true);

                spawnHeight = (double) spawnHeightField.get(happyGhast);
                hasInitializedHeight = (boolean) initializedField.get(happyGhast);
            } catch (Exception e) {
                // В случае ошибки используем текущую высоту как запасной вариант
                spawnHeight = this.ghast.getY();
                LOGGER.error("Error accessing spawn height fields", e);
            }
        } else {
            // Если это не HappyGhast, используем текущую высоту
            spawnHeight = this.ghast.getY();
        }

        // Если высота спавна не была инициализирована, используем текущую высоту
        if (!hasInitializedHeight) {
            spawnHeight = this.ghast.getY();
        }

        // Определяем границы вертикального диапазона
        double upperLimit = spawnHeight + VERTICAL_MOVEMENT_RANGE;
        double lowerLimit = spawnHeight - VERTICAL_MOVEMENT_RANGE;

        // Защита для superflat миров
        double minWorldHeight = this.ghast.level().getMinBuildHeight() + 5.0;
        lowerLimit = Math.max(lowerLimit, minWorldHeight);

        // Если гаст на привязи, используем стандартную логику, но с учетом диапазона вертикального перемещения
        double d0, d1, d2;

        if (this.ghast.isLeashed()) {
            d0 = this.ghast.getX() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
            // Используем диапазон вместо фиксированного значения
            d1 = this.ghast.getY() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 8.0F);
            d2 = this.ghast.getZ() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);

            // Ограничиваем высоту в рамках диапазона
            d1 = Math.max(lowerLimit, Math.min(d1, upperLimit));

            this.ghast.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
            return;
        }

        // Проверяем, есть ли точка привязки
        if (AnchorManager.hasAnchor(this.ghast)) {
            // Получаем точку привязки
            Vec3 anchor = AnchorManager.getAnchor(this.ghast);
            double maxRadius = AnchorManager.getMaxRadius(this.ghast);

            // Используем цилиндрический подход
            double angle = randomsource.nextFloat() * 2 * Math.PI;
            double horizontalDist = randomsource.nextFloat() * maxRadius;

            double offsetX = horizontalDist * Math.cos(angle);
            double offsetZ = horizontalDist * Math.sin(angle);

            // Используем диапазон для вертикального перемещения
            double minOffset = Math.max(-VERTICAL_MOVEMENT_RANGE, lowerLimit - spawnHeight);
            double maxOffset = Math.min(VERTICAL_MOVEMENT_RANGE, upperLimit - spawnHeight);
            double offsetY = minOffset + randomsource.nextFloat() * (maxOffset - minOffset);

            d0 = anchor.x + offsetX;
            d1 = anchor.y + offsetY;
            d2 = anchor.z + offsetZ;

            this.ghast.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
        } else {
            // Если нет данных о точке привязки, используем стандартное поведение с ограничением по диапазону
            d0 = this.ghast.getX() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);
            // Используем меньший разброс по вертикали и учитываем диапазон
            d1 = this.ghast.getY() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 4.0F);
            d2 = this.ghast.getZ() + (double)((randomsource.nextFloat() * 2.0F - 1.0F) * 16.0F);

            // Ограничиваем высоту в рамках диапазона
            d1 = Math.max(lowerLimit, Math.min(d1, upperLimit));

            this.ghast.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
        }
    }

    private int findGroundHeight(double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int startY = Mth.floor(this.ghast.getY()) - 1;
        // Увеличиваем глубину проверки для superflat миров
        int minY = Math.max(this.ghast.level().getMinBuildHeight(), startY - 256);

        // Проверяем блоки сверху вниз
        for (int y = startY; y >= minY; y--) {
            BlockPos blockPos = new BlockPos(blockX, y, blockZ);
            BlockState blockState = this.ghast.level().getBlockState(blockPos);

            if (!blockState.isAir() && !blockState.liquid()) {
                VoxelShape collisionShape = blockState.getCollisionShape(this.ghast.level(), blockPos);
                if (!collisionShape.isEmpty()) {
                    return y + 1;
                }
            }
        }

        return -1;
    }
}