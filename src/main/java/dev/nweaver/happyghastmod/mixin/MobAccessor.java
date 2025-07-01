package dev.nweaver.happyghastmod.mixin;

import net.minecraft.nbt.CompoundTag; // добавлен импорт
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable; // добавлен импорт для nullable

@Mixin(Mob.class)
public interface MobAccessor {
    @Accessor("leashHolder")
    void setLeashHolder(Entity entity);

    // оставляем существующий геттер, если он где-то используется
    @Accessor("leashHolder")
    Entity getLeashHolderAccessor();

    // добавляем доступ к leashinfotag
    @Accessor("leashInfoTag")
    @Nullable // поле может быть null
    CompoundTag getLeashInfoTag();

    @Accessor("leashInfoTag")
    void setLeashInfoTag(@Nullable CompoundTag tag);
}