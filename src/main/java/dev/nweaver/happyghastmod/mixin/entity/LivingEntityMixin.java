package dev.nweaver.happyghastmod.mixin.entity;

import dev.nweaver.happyghastmod.api.IQuadLeashTarget;
// УБРАТЬ: import dev.nweaver.happyghastmod.init.ModDataSerializers;
// УБРАТЬ: import net.minecraft.network.syncher.EntityDataAccessor;
// УБРАТЬ: import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
// УБРАТЬ: import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
// УБРАТЬ: import org.spongepowered.asm.mixin.injection.At;
// УБРАТЬ: import org.spongepowered.asm.mixin.injection.Inject;
// УБРАТЬ: import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


import java.util.Optional;
import java.util.UUID;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity implements IQuadLeashTarget {

    // --- ВОЗВРАЩАЕМ @Unique поле ---
    @Unique
    private Optional<UUID> happyghastmod$quadLeashingGhastUUID = Optional.empty();
    // --- УБИРАЕМ EntityDataAccessor и инъекцию в defineSynchedData ---

    protected LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    // --- Реализация интерфейса через @Unique поле ---
    @Override
    public Optional<UUID> getQuadLeashingGhastUUID() {
        // Возвращаем значение поля (будет актуально только на сервере)
        if (this.happyghastmod$quadLeashingGhastUUID == null) { // Доп. проверка
            this.happyghastmod$quadLeashingGhastUUID = Optional.empty();
        }
        return happyghastmod$quadLeashingGhastUUID;
    }

    @Override
    public void setQuadLeashingGhastUUID(Optional<UUID> ghastUUID) {
        // Устанавливаем значение поля (будет актуально только на сервере)
        this.happyghastmod$quadLeashingGhastUUID = ghastUUID == null ? Optional.empty() : ghastUUID;
    }
    // --- КОНЕЦ ИЗМЕНЕНИЙ ---
}