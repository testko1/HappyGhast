package dev.nweaver.happyghastmod.block;

import dev.nweaver.happyghastmod.block.entity.GhastlingIncubatorBlockEntity;
import dev.nweaver.happyghastmod.init.BlockEntityInit;
import dev.nweaver.happyghastmod.init.ItemInit;
import dev.nweaver.happyghastmod.init.SoundInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.ChatFormatting;

public class GhastlingIncubatorBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {

    // свойство waterlogged для взаимодействия с водой
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // свойство для стадии инкубации
    public static final EnumProperty<IncubationStage> INCUBATION_STAGE = EnumProperty.create("incubation_stage", IncubationStage.class);

    // свойство для направления блока
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE_NORTH;
    private static final VoxelShape SHAPE_EAST;
    private static final VoxelShape SHAPE_SOUTH;
    private static final VoxelShape SHAPE_WEST;

    static {
        // создаем базовый хитбокс для направления north
        SHAPE_NORTH = createShapeForDirection(Direction.NORTH);
        SHAPE_EAST = createShapeForDirection(Direction.EAST);
        SHAPE_SOUTH = createShapeForDirection(Direction.SOUTH);
        SHAPE_WEST = createShapeForDirection(Direction.WEST);
    }

    private static VoxelShape createShapeForDirection(Direction direction) {
        VoxelShape shape = Shapes.empty();

        // центральный элемент (всегда один и тот же, тк симметричен по оси y)
        shape = Shapes.or(shape, Shapes.box(3/16.0, 0, 3/16.0, 13/16.0, 10/16.0, 13/16.0));

        // горизонтальные элементы, которые нужно вращать в зависимости от направления
        switch(direction) {
            case NORTH:
                // горизонтальный элемент 1
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 5/16.0, 15/16.0, 2/16.0, 7/16.0));
                // горизонтальный элемент 2
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 10/16.0, 15/16.0, 2/16.0, 12/16.0));
                break;

            case EAST:
                // поворот на 90 градусов
                // горизонтальный элемент 1
                shape = Shapes.or(shape, Shapes.box(9/16.0, 0, 1/16.0, 11/16.0, 2/16.0, 15/16.0));
                // горизонтальный элемент 2
                shape = Shapes.or(shape, Shapes.box(4/16.0, 0, 1/16.0, 6/16.0, 2/16.0, 15/16.0));
                break;

            case SOUTH:
                // поворот на 180 градусов
                // горизонтальный элемент 1
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 9/16.0, 15/16.0, 2/16.0, 11/16.0));
                // горизонтальный элемент 2
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 4/16.0, 15/16.0, 2/16.0, 6/16.0));
                break;

            case WEST:
                // поворот на 270 градусов
                // горизонтальный элемент 1
                shape = Shapes.or(shape, Shapes.box(5/16.0, 0, 1/16.0, 7/16.0, 2/16.0, 15/16.0));
                // горизонтальный элемент 2
                shape = Shapes.or(shape, Shapes.box(10/16.0, 0, 1/16.0, 12/16.0, 2/16.0, 15/16.0));
                break;

            default:
                // на всякий случай, для перестраховки
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 5/16.0, 15/16.0, 2/16.0, 7/16.0));
                shape = Shapes.or(shape, Shapes.box(1/16.0, 0, 10/16.0, 15/16.0, 2/16.0, 12/16.0));
                break;
        }

        return shape;
    }

    public GhastlingIncubatorBlock() {
        super(Block.Properties.of()
                .mapColor(MapColor.COLOR_GRAY)
                .strength(1.5F, 6.0F)  // оставляем прочность, но делаем блок ломаемым рукой
                .sound(SoundType.STONE)
                .noOcclusion()  // важно для правильного рендеринга частичных блоков
        );
        // устанавливаем значения по умолчанию
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(WATERLOGGED, Boolean.valueOf(false))
                .setValue(INCUBATION_STAGE, IncubationStage.DRIED)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, INCUBATION_STAGE, FACING);
    }

    // метод для определения формы блока для рендера и коллизий
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    // возвращаем такую же форму для коллизий
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    // метод для определения стадии на основе процента инкубации
    public static IncubationStage getStageFromProgress(int progress, int targetProgress) {
        float percentage = (float) progress / targetProgress * 100f;

        if (percentage >= 66.7f) {
            return IncubationStage.HAPPY;
        } else if (percentage >= 33.3f) {
            return IncubationStage.NEUTRAL;
        } else {
            return IncubationStage.DRIED;
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack itemInHand = player.getItemInHand(hand);

        // логика выполняется только на сервере
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof GhastlingIncubatorBlockEntity incubatorBe) {

                // проверка на слезу гаста
                if (itemInHand.is(Items.GHAST_TEAR)) {
                    boolean isWaterlogged = state.getValue(WATERLOGGED);
                    int currentTicks = incubatorBe.getIncubationTicks();
                    int targetTicks = GhastlingIncubatorBlockEntity.TARGET_INCUBATION_TICKS;

                    if (isWaterlogged && currentTicks < targetTicks) {
                        boolean didComplete = incubatorBe.accelerateIncubation();
                        if (!player.getAbilities().instabuild) {
                            itemInHand.shrink(1);
                        }
                        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                        if (level instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.SOUL,
                                    pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D,
                                    10, 0.3D, 0.3D, 0.3D, 0.05D);
                        }

                        // обновляем стадию инкубации после ускорения
                        updateIncubationStage(level, pos, incubatorBe);

                        if (didComplete) {
                            player.sendSystemMessage(Component.literal("Incubation complete").withStyle(ChatFormatting.LIGHT_PURPLE));
                        }
                        return InteractionResult.CONSUME; // слеза использована
                    } else if (!isWaterlogged) {
                        player.sendSystemMessage(Component.literal("Incubator must be waterlogged to accelerate").withStyle(ChatFormatting.YELLOW));
                        return InteractionResult.FAIL; // действие не выполнено
                    } else {
                        player.sendSystemMessage(Component.literal("Incubation is already complete").withStyle(ChatFormatting.GRAY));
                        return InteractionResult.PASS; // ничего не произошло
                    }
                }

                // проверка на палку (для отображения прогресса)
                else if (itemInHand.is(Items.STICK)) {
                    int currentTicks = incubatorBe.getIncubationTicks();
                    int targetTicks = GhastlingIncubatorBlockEntity.TARGET_INCUBATION_TICKS;

                    if (targetTicks > 0) {
                        int progressPercent = (int) (((double) currentTicks / targetTicks) * 100.0);
                        long remainingSeconds = (targetTicks - currentTicks) / 20L;
                        long remainingMinutes = remainingSeconds / 60L;
                        remainingSeconds %= 60;

                        Component message;
                        boolean isWaterlogged = state.getValue(WATERLOGGED);
                        if (isWaterlogged) {
                            message = Component.literal("Incubation progress: ")
                                    .append(Component.literal(progressPercent + "%").withStyle(ChatFormatting.AQUA))
                                    .append(Component.literal(" (approx. "))
                                    .append(Component.literal(String.format("%d min %d sec", remainingMinutes, remainingSeconds)).withStyle(ChatFormatting.GRAY))
                                    .append(Component.literal(" remaining while waterlogged)"));
                        } else {
                            message = Component.literal("Incubator must be waterlogged, Progress: ")
                                    .append(Component.literal(progressPercent + "%").withStyle(ChatFormatting.GRAY));
                        }
                        player.sendSystemMessage(message);
                    } else {
                        player.sendSystemMessage(Component.literal("Incubation target time not set correctly").withStyle(ChatFormatting.RED));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // обновление стадии инкубации
    private void updateIncubationStage(Level level, BlockPos pos, GhastlingIncubatorBlockEntity entity) {
        if (!level.isClientSide) {
            BlockState currentState = level.getBlockState(pos);
            IncubationStage oldStage = currentState.getValue(INCUBATION_STAGE);
            IncubationStage newStage = getStageFromProgress(
                    entity.getIncubationTicks(),
                    GhastlingIncubatorBlockEntity.TARGET_INCUBATION_TICKS
            );

            // обновляем состояние блока, только если стадия изменилась
            if (oldStage != newStage) {
                // звук смены состояния
                level.playSound(null, pos, SoundInit.DRIED_GHAST_STATE_CHANGE.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                level.setBlock(pos, currentState.setValue(INCUBATION_STAGE, newStage), Block.UPDATE_ALL);
            }
        }
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // проверяем, есть ли вода в позиции размещения
        FluidState fluidstate = context.getLevel().getFluidState(context.getClickedPos());
        boolean isWater = fluidstate.getType() == Fluids.WATER;

        // получаем направление из контекста размещения
        Direction direction = context.getHorizontalDirection().getOpposite();

        // проигрываем звук размещения в зависимости от наличия воды
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide()) {
            if (isWater) {
                level.playSound(null, pos, SoundInit.DRIED_GHAST_PLACE_IN_WATER.get(),
                        SoundSource.BLOCKS, 0.8F, 0.96F);
            } else {
            }
        }

        return this.defaultBlockState()
                .setValue(WATERLOGGED, Boolean.valueOf(isWater))  // устанавливаем свойство waterlogged
                .setValue(INCUBATION_STAGE, IncubationStage.DRIED)
                .setValue(FACING, direction);
    }

    // важно для поддержки взаимодействия с водой
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    // обновляем блок, когда изменения происходят рядом
    @Override
    public BlockState updateShape(BlockState stateIn, Direction facing, BlockState facingState, LevelAccessor levelAccessor, BlockPos currentPos, BlockPos facingPos) {
        if (stateIn.getValue(WATERLOGGED)) {
            // стандартное обновление для waterlogged блоков
            levelAccessor.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(levelAccessor));
        }

        // проверяем изменение состояния waterlogged блока
        Level level = levelAccessor instanceof Level ? (Level) levelAccessor : null;
        if (level != null && !level.isClientSide()) {
            BlockState oldState = level.getBlockState(currentPos);
            boolean wasWaterlogged = oldState.getValue(WATERLOGGED);
            boolean isNowWaterlogged = stateIn.getValue(WATERLOGGED);

            if (wasWaterlogged != isNowWaterlogged) {
                // воспроизводим звук смены состояния
                level.playSound(null, currentPos, SoundInit.DRIED_GHAST_STATE_CHANGE.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        return super.updateShape(stateIn, facing, facingState, levelAccessor, currentPos, facingPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GhastlingIncubatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(blockEntityType, BlockEntityInit.GHASTLING_INCUBATOR.get(), GhastlingIncubatorBlockEntity::serverTick);
    }

    // переопределяем метод для выпадения предмета при разрушении
    // в forge 1.20.1 используем этот метод вместо getdrops()
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        // звук разрушения
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundInit.DRIED_GHAST_BREAK.get(),
                    SoundSource.BLOCKS, 0.8F, 0.96F);
        }

        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        if (!level.isClientSide && blockEntity instanceof GhastlingIncubatorBlockEntity incubator) {
            int progress = incubator.getIncubationTicks();
            int maxProgress = GhastlingIncubatorBlockEntity.TARGET_INCUBATION_TICKS;
            float progressPercentage = (float) progress / maxProgress;

            // создаем предмет для выпадения
            ItemStack dropStack = new ItemStack(ItemInit.GHASTLING_INCUBATOR_ITEM.get());

            // сохраняем прогресс инкубации в nbt предмета
            if (progress > 0) {
                dropStack.getOrCreateTag().putInt("IncubationProgress", progress);

                // добавляем подсказку о прогрессе
                if (progressPercentage >= 0.05f) {
                    String tooltip = String.format("Incubation: %.1f%%", progressPercentage * 100);
                    dropStack.getOrCreateTag().putString("IncubationTooltip", tooltip);
                }
            }

            // выбрасываем предмет в мир
            popResource(level, pos, dropStack);
        }
    }

    // добавляем метод для обработки звука удара
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            level.playSound(null, pos, SoundInit.DRIED_GHAST_HIT.get(),
                    SoundSource.BLOCKS, 0.2F, 0.6F);
        }
        super.attack(state, level, pos, player);
    }

    // метод для воспроизведения эмбиент звуков из блок-энтити
    public static void playAmbientSound(Level level, BlockPos pos, boolean isWaterlogged) {
        if (!level.isClientSide && level.random.nextInt(300) < 5) { // ~1.7% шанс на тик
            if (isWaterlogged) {
                level.playSound(null, pos, SoundInit.DRIED_GHAST_AMBIENT_WATER.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                level.playSound(null, pos, SoundInit.DRIED_GHAST_AMBIENT.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}