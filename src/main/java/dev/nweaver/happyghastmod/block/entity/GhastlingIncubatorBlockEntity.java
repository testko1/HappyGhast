package dev.nweaver.happyghastmod.block.entity;

import dev.nweaver.happyghastmod.block.GhastlingIncubatorBlock;
import dev.nweaver.happyghastmod.block.IncubationStage;
import dev.nweaver.happyghastmod.entity.Ghastling;
import dev.nweaver.happyghastmod.init.BlockEntityInit;
import dev.nweaver.happyghastmod.init.EntityInit;
import dev.nweaver.happyghastmod.init.SoundInit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class GhastlingIncubatorBlockEntity extends BlockEntity {

    // Время инкубации в тиках (1 майнкрафт день = 20 минут = 1200 секунд = 24000 тиков)
    public static final int TARGET_INCUBATION_TICKS = 24000;
    private static final int ACCELERATION_TICKS_PER_USE = 2400;
    private int incubationTicks = 0;
    private IncubationStage lastStage = IncubationStage.DRIED; // Для отслеживания изменений

    // Отслеживаем состояние waterlogged
    private boolean wasWaterlogged = false;

    // Таймер для ambient звуков
    private int ambientSoundTimer = 0;
    private static final int AMBIENT_SOUND_INTERVAL = 200; // Примерно 10 секунд между проверками

    public GhastlingIncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityInit.GHASTLING_INCUBATOR.get(), pos, state);
        wasWaterlogged = state.getValue(GhastlingIncubatorBlock.WATERLOGGED);
    }

    public int getIncubationTicks() {
        return this.incubationTicks;
    }

    public boolean accelerateIncubation() {
        if (this.level == null || this.level.isClientSide) {
            return false; // Не выполняем на клиенте
        }
        if (this.incubationTicks >= TARGET_INCUBATION_TICKS) {
            return false; // Уже завершено
        }

        // Добавляем тики
        this.incubationTicks += ACCELERATION_TICKS_PER_USE;

        // Ограничиваем сверху максимальным значением
        this.incubationTicks = Mth.clamp(this.incubationTicks, 0, TARGET_INCUBATION_TICKS);

        // Обновляем модель блока
        updateIncubationStage();

        // Помечаем для сохранения
        setChanged();

        // Проверяем, не завершилась ли инкубация прямо сейчас
        if (this.incubationTicks >= TARGET_INCUBATION_TICKS) {
            // Воспроизводим звук окончания инкубации/спавна гастлинга
            this.level.playSound(null, this.worldPosition, SoundInit.GHASTLING_SPAWN.get(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);

            // Да, завершилась! Запускаем спавн и уничтожение блока
            spawnGhastling(this.level, this.worldPosition);
            this.level.destroyBlock(this.worldPosition, false);
            return true; // Сообщаем, что завершили
        }

        // Инкубация еще не завершена
        return false;
    }

    // Метод для обновления стадии инкубации
    private void updateIncubationStage() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState currentState = this.level.getBlockState(this.worldPosition);

            // Определяем текущую стадию на основе прогресса
            IncubationStage currentStage = GhastlingIncubatorBlock.getStageFromProgress(
                    this.incubationTicks, TARGET_INCUBATION_TICKS);

            // Если стадия изменилась, обновляем блок
            if (currentStage != lastStage) {
                // Воспроизводим звук изменения стадии инкубации
                this.level.playSound(null, this.worldPosition, SoundInit.DRIED_GHAST_STATE_CHANGE.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                this.level.setBlock(
                        this.worldPosition,
                        currentState.setValue(GhastlingIncubatorBlock.INCUBATION_STAGE, currentStage),
                        Block.UPDATE_ALL
                );
                lastStage = currentStage;
            }
        }
    }

    // Логика тиков (вызывается на сервере)
    public static void serverTick(Level level, BlockPos pos, BlockState state, GhastlingIncubatorBlockEntity blockEntity) {
        boolean isWaterlogged = state.getValue(GhastlingIncubatorBlock.WATERLOGGED);

        // Проверяем, изменилось ли состояние waterlogged
        if (isWaterlogged != blockEntity.wasWaterlogged) {
            // Воспроизводим звук изменения уровня гидрации
            level.playSound(null, pos, SoundInit.DRIED_GHAST_STATE_CHANGE.get(),
                    SoundSource.BLOCKS, 1.0F, 1.0F);
            blockEntity.wasWaterlogged = isWaterlogged;
        }

        // Обрабатываем ambient звуки
        blockEntity.ambientSoundTimer++;
        if (blockEntity.ambientSoundTimer >= AMBIENT_SOUND_INTERVAL) {
            blockEntity.ambientSoundTimer = 0;
            // Вызываем метод воспроизведения ambient звука
            GhastlingIncubatorBlock.playAmbientSound(level, pos, isWaterlogged);
        }

        if (blockEntity.incubationTicks >= TARGET_INCUBATION_TICKS) {
            return;
        }

        if (isWaterlogged) {
            blockEntity.incubationTicks++;

            // Обновляем стадию каждый тик, если инкубатор находится в воде
            blockEntity.updateIncubationStage();

            if (blockEntity.incubationTicks >= TARGET_INCUBATION_TICKS) {
                // Воспроизводим звук спавна гастлинга
                level.playSound(null, pos, SoundInit.GHASTLING_SPAWN.get(),
                        SoundSource.BLOCKS, 1.0F, 1.0F);

                spawnGhastling(level, pos);
                level.destroyBlock(pos, false);
            } else {
                blockEntity.setChanged();
            }
        } else {
            if (blockEntity.incubationTicks > 0) {
                blockEntity.incubationTicks = 0;

                // Сбрасываем стадию на DRIED, если блок больше не в воде
                BlockState currentState = level.getBlockState(pos);
                if (currentState.getValue(GhastlingIncubatorBlock.INCUBATION_STAGE) != IncubationStage.DRIED) {
                    level.setBlock(
                            pos,
                            currentState.setValue(GhastlingIncubatorBlock.INCUBATION_STAGE, IncubationStage.DRIED),
                            Block.UPDATE_ALL
                    );
                    blockEntity.lastStage = IncubationStage.DRIED;

                    // Воспроизводим звук изменения состояния
                    level.playSound(null, pos, SoundInit.DRIED_GHAST_STATE_CHANGE.get(),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                }

                blockEntity.setChanged();
                System.out.println("Incubator at " + pos + " is no longer waterlogged, progress reset.");
            }
        }
    }

    // Вспомогательный метод для спавна гастлинга
    private static void spawnGhastling(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            EntityType<Ghastling> ghastlingType = EntityInit.GHASTLING.get();
            Vec3 spawnPos = Vec3.atCenterOf(pos).add(0, 0.6, 0);

            Ghastling ghastling = ghastlingType.create(serverLevel);

            if (ghastling != null) {
                ghastling.setPos(spawnPos.x(), spawnPos.y(), spawnPos.z());

                if (serverLevel.addFreshEntity(ghastling)) {
                    System.out.println("Ghastling spawned from incubator at " + pos);

                    // Воспроизводим звук спавна гастлинга
                    level.playSound(null, pos, SoundInit.GHASTLING_SPAWN.get(),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                } else {
                    System.err.println("Failed to add Ghastling to world at " + pos);
                }
            } else {
                System.err.println("Failed to create Ghastling instance for incubator at " + pos);
            }
        }
    }

    // Сохранение и загрузка
    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        nbt.putInt("IncubationTicks", this.incubationTicks);
        nbt.putString("LastStage", this.lastStage.getSerializedName());
        nbt.putBoolean("WasWaterlogged", this.wasWaterlogged);
    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        this.incubationTicks = nbt.getInt("IncubationTicks");
        this.wasWaterlogged = nbt.getBoolean("WasWaterlogged");

        // Загружаем последнюю стадию, если она сохранена
        if (nbt.contains("LastStage")) {
            String stageName = nbt.getString("LastStage");
            for (IncubationStage stage : IncubationStage.values()) {
                if (stage.getSerializedName().equals(stageName)) {
                    this.lastStage = stage;
                    break;
                }
            }
        }
    }
}