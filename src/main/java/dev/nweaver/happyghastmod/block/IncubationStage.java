package dev.nweaver.happyghastmod.block;

import net.minecraft.util.StringRepresentable;

public enum IncubationStage implements StringRepresentable {
    DRIED("dried"),
    NEUTRAL("neutral"),
    HAPPY("happy");

    private final String name;

    IncubationStage(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}