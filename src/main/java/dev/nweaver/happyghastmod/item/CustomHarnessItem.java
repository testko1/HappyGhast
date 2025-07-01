package dev.nweaver.happyghastmod.item;

import dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;

// расширенный класс предмета сбруи с поддержкой кастомных текстур
public class CustomHarnessItem extends HarnessItem {
    private static final Logger LOGGER = LogManager.getLogger();

    public CustomHarnessItem(Properties properties, String color) {
        super(properties, color);
    }

    @Override
    public Component getName(ItemStack stack) {
        // проверяем, является ли это кастомной сбруей
        if (stack.hasTag() && stack.getTag().contains("CustomHarnessId")) {
            String customName = stack.getTag().getString("CustomHarnessName");
            return Component.literal(customName);
        }

        // иначе используем стандартное имя
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        // добавляем информацию о кастомной сбруе
        if (stack.hasTag() && stack.getTag().contains("CustomHarnessId")) {
            String harnessId = stack.getTag().getString("CustomHarnessId");

            if (level != null && level.isClientSide) {
                // на клиенте добавляем информацию, если она доступна
                ClientCustomHarnessManager.HarnessInfo info = ClientCustomHarnessManager.getCustomHarnessInfo(harnessId);
                if (info != null) {
                    tooltip.add(Component.translatable("tooltip.happyghastmod.custom_harness_creator",
                            info.getCreatorName()));
                }
            }
        }

        // добавляем стандартный текст подсказки
        tooltip.add(Component.translatable("tooltip.happyghastmod.harness"));

        super.appendHoverText(stack, level, tooltip, flag);
    }

    // получает айди кастомной сбруи
    public static String getCustomHarnessId(ItemStack stack) {
        if (stack.hasTag() && stack.getTag().contains("CustomHarnessId")) {
            return stack.getTag().getString("CustomHarnessId");
        }
        return null;
    }

    // получает цвет сбруи, кастомной или стандартной
    @Override
    public String getColor() {
        // для кастомных сбруй это должно обрабатываться рендерером
        // на основе айди кастомной сбруи
        return super.getColor();
    }

}