package dev.nweaver.happyghastmod.entity.components;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.nbt.CompoundTag;

public class GhastDataComponent {
    private final HappyGhast owner;

    public GhastDataComponent(HappyGhast owner) {
        this.owner = owner;
    }

    // проверка наличия седла
    public boolean isSaddled() {
        return owner.isSaddled();
    }

    public void setSaddled(boolean saddled) {
        owner.setSaddled(saddled);
    }

    // получить цвет сбруи
    public String getHarnessColor() {
        return owner.getHarnessColor();
    }

    // установить цвет сбруи
    public void setHarnessColor(String color) {
        owner.setHarnessColor(color);
    }

    // проверка подъема вверх
    public boolean isAscending() {
        return owner.isAscending();
    }


    // проверка спуска вниз
    public boolean isDescending() {
        return owner.isDescending();
    }

    // множитель скорости
    public float getSpeedMultiplier() {
        return owner.getSpeedMultiplier();
    }

    public void setSpeedMultiplier(float speedMultiplier) {
        owner.setSpeedMultiplier(speedMultiplier);
    }

    // сохраняем данные
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putBoolean("BlueHarness", isSaddled());
        if (isSaddled()) {
            compound.putString("HarnessColor", getHarnessColor());
        }

        compound.putFloat("SpeedMultiplier", getSpeedMultiplier());
    }

    // загружаем данные
    public void readAdditionalSaveData(CompoundTag compound) {
        // совместимость со старыми версиями
        if (compound.contains("Saddle")) {
            setSaddled(compound.getBoolean("Saddle"));
        } else {
            setSaddled(compound.getBoolean("BlueHarness"));
        }

        // смотрим цвет сбруи
        if (compound.contains("HarnessColor")) {
            setHarnessColor(compound.getString("HarnessColor"));
        } else if (isSaddled()) {
            setHarnessColor("blue");
        }

        // загрузка множителя скорости
        if (compound.contains("SpeedMultiplier")) {
            setSpeedMultiplier(compound.getFloat("SpeedMultiplier"));
        } else {
            // значение по умолчанию
            setSpeedMultiplier(GhastMovementComponent.SPEED_MULTIPLIER);
        }
    }
}