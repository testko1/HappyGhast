package dev.nweaver.happyghastmod.entity.goals;

import java.util.EnumSet;

import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.entity.AnchorManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GhastlingRandomFloatGoal extends Goal {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Ghastling ghastling;

    public GhastlingRandomFloatGoal(Ghastling ghastling) {
        this.ghastling = ghastling;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // Если гастлинг следует за игроком, не используем случайное движение
        if (AnchorManager.isFollowing(this.ghastling)) {
            return false;
        }

        MoveControl movecontrol = this.ghastling.getMoveControl();
        if (!movecontrol.hasWanted()) {
            return true;
        } else {
            double d0 = movecontrol.getWantedX() - this.ghastling.getX();
            double d1 = movecontrol.getWantedY() - this.ghastling.getY();
            double d2 = movecontrol.getWantedZ() - this.ghastling.getZ();
            double d3 = d0 * d0 + d1 * d1 + d2 * d2;
            return d3 < 1.0D || d3 > 3600.0D;
        }
    }

    @Override
    public boolean canContinueToUse() {
        return false; // Выполняется однократно
    }

    @Override
    public void start() {
        RandomSource randomsource = this.ghastling.getRandom();

        // Повторяем выбор точки до 5 раз, если она оказывается в воде
        double d0 = 0, d1 = 0, d2 = 0;
        boolean validPointFound = false;

        for (int attempt = 0; attempt < 5 && !validPointFound; attempt++) {
            // Получаем координаты в зависимости от режима
            if (this.ghastling.isLeashed()) {
                d0 = this.ghastling.getX() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 6.0F);
                d1 = this.ghastling.getY() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 4.0F);
                d2 = this.ghastling.getZ() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 6.0F);
            } else if (AnchorManager.hasAnchor(this.ghastling)) {
                Vec3 anchor = AnchorManager.getAnchor(this.ghastling);
                double maxRadius = AnchorManager.getMaxRadius(this.ghastling);
                double angle = randomsource.nextFloat() * 2 * Math.PI;
                double horizontalDist = randomsource.nextFloat() * maxRadius * 0.8;

                d0 = anchor.x + horizontalDist * Math.cos(angle);
                d2 = anchor.z + horizontalDist * Math.sin(angle);

                // Определяем высоту земли под точкой
                int groundY = findGroundHeight(d0, d2);
                if (groundY != -1) {
                    // Если нашли землю, держимся в 1-3 блоках над ней
                    d1 = groundY + 1 + randomsource.nextFloat() * 2.0;
                } else {
                    // Если землю не нашли, держимся ближе к якорю вертикально
                    d1 = anchor.y + (-2.0 + randomsource.nextFloat() * 4.0);

                    // И не выше максимальной допустимой высоты
                    int minBuildHeight = this.ghastling.level().getMinBuildHeight();
                    int maxHeightAboveVoid = minBuildHeight + 20 + randomsource.nextInt(10);
                    d1 = Math.min(d1, maxHeightAboveVoid);
                }
            } else {
                d0 = this.ghastling.getX() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 8.0F);
                d1 = this.ghastling.getY() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 3.0F);
                d2 = this.ghastling.getZ() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 8.0F);

                // Проверяем высоту земли
                int groundY = findGroundHeight(d0, d2);
                if (groundY != -1) {
                    // Если нашли землю, держимся близко к ней
                    d1 = groundY + 1 + randomsource.nextFloat() * 2.0;
                } else {
                    // Если землю не нашли, не поднимаемся слишком высоко
                    int minBuildHeight = this.ghastling.level().getMinBuildHeight();
                    int maxHeightAboveVoid = minBuildHeight + 20 + randomsource.nextInt(10);
                    d1 = Math.min(d1, maxHeightAboveVoid);
                }
            }

            // Проверяем, не в воде ли точка
            BlockPos targetPos = new BlockPos(Mth.floor(d0), Mth.floor(d1), Mth.floor(d2));
            FluidState fluidState = this.ghastling.level().getFluidState(targetPos);

            if (fluidState.isEmpty() || !fluidState.is(FluidTags.WATER)) {
                // Проверяем также блоки ниже, чтобы не парить прямо над водой
                boolean waterBelow = false;
                for (int y = Mth.floor(d1) - 1; y > Mth.floor(d1) - 3 && y > 0; y--) {
                    BlockPos belowPos = new BlockPos(Mth.floor(d0), y, Mth.floor(d2));
                    FluidState belowFluid = this.ghastling.level().getFluidState(belowPos);

                    if (!belowFluid.isEmpty() && belowFluid.is(FluidTags.WATER)) {
                        waterBelow = true;
                        break;
                    } else if (!this.ghastling.level().getBlockState(belowPos).isAir()) {
                        // Нашли твердый блок - останавливаем поиск
                        break;
                    }
                }

                // Если прямо под целевой точкой вода, увеличиваем высоту
                if (waterBelow) {
                    d1 += 2.0; // Поднимаемся выше над водой
                }

                validPointFound = true;
            }
        }

        // Для безопасности: если не нашли подходящую точку после всех попыток
        if (!validPointFound) {
            // Выбираем текущее положение с небольшим смещением
            d0 = this.ghastling.getX() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 2.0F);
            d1 = this.ghastling.getY();
            d2 = this.ghastling.getZ() + ((randomsource.nextFloat() * 2.0F - 1.0F) * 2.0F);
        }

        this.ghastling.getMoveControl().setWantedPosition(d0, d1, d2, 1.0D);
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
        int minY = Math.max(0, startY - 20); // Проверяем максимум 20 блоков вниз

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