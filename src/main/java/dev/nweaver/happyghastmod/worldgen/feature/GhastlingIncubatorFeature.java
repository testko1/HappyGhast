package dev.nweaver.happyghastmod.worldgen.feature;

import com.mojang.serialization.Codec;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.init.BlockInit;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

import java.util.ArrayList;
import java.util.List;

public class GhastlingIncubatorFeature extends Feature<NoneFeatureConfiguration> {

    private static final int MAX_SEARCH_RADIUS = 15; // радиус для поиска костяных блоков
    private static final int PLACEMENT_RADIUS = 5;   // радиус для размещения вокруг костяных блоков

    private static final float SPAWN_CHANCE = 1f; // шанс спавна 100% когда фича размещается

    // минимальное расстояние между инкубаторами
    private static final int MIN_DISTANCE_BETWEEN_INCUBATORS = 10;

    public GhastlingIncubatorFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        // убрали лишний лог info для каждой попытки размещения
        // теперь логируем только в trace (для отладки при необходимости)
        //if (HappyGhastMod.LOGGER.isTraceEnabled()) {
        //    HappyGhastMod.LOGGER.trace("GhastlingIncubatorFeature.place called at {}", origin);
        //}

        // сначала проверяем шанс спавна - ранний выход при неудаче
        if (random.nextFloat() >= SPAWN_CHANCE) { // изменено с > на >= для большей точности
            return false;
        }

        // найти все костяные блоки в радиусе
        List<BlockPos> bonePositions = findAllBoneBlocks(level, origin, MAX_SEARCH_RADIUS);

        if (bonePositions.isEmpty()) {
            // логируем только на уровне trace
            //if (HappyGhastMod.LOGGER.isTraceEnabled()) {
            //    HappyGhastMod.LOGGER.trace("No bone blocks found near {}", origin);
            //}
            return false;
        }

        // вместо info используем debug для важных, но не критичных сообщений
        //if (HappyGhastMod.LOGGER.isDebugEnabled()) {
        //    HappyGhastMod.LOGGER.debug("Found {} bone blocks near {}", bonePositions.size(), origin);
        //}

        // пытаемся разместить рядом с каждым костяным блоком, начиная со случайного
        int startIndex = random.nextInt(bonePositions.size());
        for (int i = 0; i < bonePositions.size(); i++) {
            // получаем позицию кости
            int index = (startIndex + i) % bonePositions.size();
            BlockPos bonePos = bonePositions.get(index);

            // проверяем, есть ли засуш гаст поблизости
            if (hasIncubatorNearby(level, bonePos, MIN_DISTANCE_BETWEEN_INCUBATORS)) {
                // логируем только на уровне trace
                //if (HappyGhastMod.LOGGER.isTraceEnabled()) {
                    //HappyGhastMod.LOGGER.trace("Skipping bone at {} - another incubator already exists nearby", bonePos);
                //}
                continue;
            }

            // ищем валидные позиции для размещения вокруг этой кости
            List<BlockPos> validPositions = findValidPlacementPositions(level, bonePos, PLACEMENT_RADIUS);

            if (!validPositions.isEmpty()) {
                // выбираем случайную позицию из валидных
                BlockPos placePos = validPositions.get(random.nextInt(validPositions.size()));

                // размещаем засуш гаста
                if (placeIncubator(level, placePos)) {
                    return true; // успешно размещен
                }
            }
        }

        // не удалось нигде разместить
        if (HappyGhastMod.LOGGER.isDebugEnabled()) {
            //HappyGhastMod.LOGGER.debug("Could not find suitable placement position for incubator near any bone block");
        }
        return false;
    }

    // проверяет, есть ли другой инкубатор поблизости
    private boolean hasIncubatorNearby(WorldGenLevel level, BlockPos center, int radius) {
        // проверяем в кубе вокруг центральной позиции
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                    // проверяем, является ли блок засуш гастом
                    if (level.getBlockState(mutablePos).is(BlockInit.GHASTLING_INCUBATOR.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // находит все костяные блоки в указанном радиусе
    private List<BlockPos> findAllBoneBlocks(WorldGenLevel level, BlockPos center, int maxRadius) {
        List<BlockPos> bonePositions = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int r = 3; r <= maxRadius; r += 3) {
            int found = 0;
            int sqRadius = r * r;

            // проверяем куб и фильтруем по квадрату расстояния для грубой сферы
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        // примерная проверка сферического расстояния
                        if (x*x + y*y + z*z > sqRadius) continue;

                        mutablePos.set(center.getX() + x, center.getY() + y, center.getZ() + z);

                        if (level.getBlockState(mutablePos).is(Blocks.BONE_BLOCK)) {
                            bonePositions.add(mutablePos.immutable());
                            found++;

                            // ранний выход, если нашли достаточно костей
                            if (found >= 5) {
                                return bonePositions;
                            }
                        }
                    }
                }
            }

            // если нашли кости в этом радиусе, прекращаем поиск
            if (!bonePositions.isEmpty()) {
                break;
            }
        }

        return bonePositions;
    }

    // находит все валидные позиции для размещения вокруг костяного блока
    private List<BlockPos> findValidPlacementPositions(WorldGenLevel level, BlockPos bonePos, int radius) {
        List<BlockPos> validPositions = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos belowPos = new BlockPos.MutableBlockPos();

        // проверяем в сфере вокруг костяного блока
        int sqRadius = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    // примерная сферическая проверка
                    if (x*x + y*y + z*z > sqRadius) continue;

                    mutablePos.set(bonePos.getX() + x, bonePos.getY() + y, bonePos.getZ() + z);
                    belowPos.set(mutablePos.getX(), mutablePos.getY() - 1, mutablePos.getZ());

                    // проверяем, является ли текущий блок воздухом и подходит ли блок снизу
                    if (level.isEmptyBlock(mutablePos) && isSuitableGround(level, belowPos)) {
                        validPositions.add(mutablePos.immutable());

                        // оптимизация: ограничиваем количество валидных позиций для проверки
                        if (validPositions.size() >= 10) {
                            return validPositions;
                        }
                    }
                }
            }
        }

        return validPositions;
    }

    // проверяет, подходит ли земля для размещения (песок/почва душ)
    private boolean isSuitableGround(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.SOUL_SAND) || state.is(Blocks.SOUL_SOIL);
    }

    // размещает высохшего гаста в указанной позиции
    private boolean placeIncubator(WorldGenLevel level, BlockPos pos) {
        boolean success = level.setBlock(pos, BlockInit.GHASTLING_INCUBATOR.get().defaultBlockState(), 3);

        if (success) {
            //HappyGhastMod.LOGGER.info("✓ GhastlingIncubatorFeature successfully placed at {}", pos);
        } else {
            HappyGhastMod.LOGGER.warn("✗ Failed to place GhastlingIncubatorFeature at {}", pos);
        }

        return success;
    }
}