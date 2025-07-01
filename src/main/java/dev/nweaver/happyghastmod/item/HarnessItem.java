package dev.nweaver.happyghastmod.item;

import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

public class HarnessItem extends Item {
    protected String color;

    public HarnessItem(Properties properties, String color) {
        super(properties);
        this.color = color;
    }

    public String getColor() {
        return color;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.happyghastmod." + color + "_harness");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.happyghastmod.harness"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}