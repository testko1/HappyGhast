package dev.nweaver.happyghastmod.entity.components;

import dev.nweaver.happyghastmod.container.GhastContainer;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.item.CustomHarnessItem;
import dev.nweaver.happyghastmod.item.HarnessItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class GhastInventoryComponent implements Container {
    private static final Logger LOGGER = LogManager.getLogger();
    private final HappyGhast owner;
    private final GhastDataComponent dataComponent;
    private final SimpleContainer inventory;
    private final List<ContainerListener> listeners = new ArrayList<>();
    private static final boolean DEBUG_LOGGING = false; // логирование отключено


    public GhastInventoryComponent(HappyGhast owner, GhastDataComponent dataComponent) {
        this.owner = owner;
        this.dataComponent = dataComponent;
        this.inventory = new SimpleContainer(1); // 1 слот под сбрую
        this.syncInventoryWithState(); // синхронизируем при создании
        this.inventory.addListener(this::onInventoryChanged); // слушатель на изменение инвентаря
    }

    // открытие гуи инвентаря
    public void openInventory(Player player) {
        if (!owner.level().isClientSide && player instanceof ServerPlayer serverPlayer &&
                owner.isVehicle() && owner.hasPassenger(player)) {
            // сначала синхронизируем
            syncInventoryWithState();
            // создаем провайдер меню
            SimpleMenuProvider provider = new SimpleMenuProvider(
                    (windowId, playerInventory, p) -> new GhastContainer(windowId, playerInventory, this, owner),
                    Component.translatable("container.happyghastmod.ghast")
            );
            // открываем экран и передаем id гаста
            NetworkHooks.openScreen(serverPlayer, provider, buffer -> {
                buffer.writeInt(owner.getId());
            });
        }
    }

    // может ли игрок юзать инвентарь
    public boolean canUseInventory(Player player) {
        // можно, если сидишь на нем
        return owner.isVehicle() && owner.hasPassenger(player);
    }

    // когда инвентарь меняется
    private void onInventoryChanged(Container container) {
        ItemStack saddleStack = container.getItem(0);
        // если слот пуст - снимаем седло
        if (saddleStack.isEmpty()) {
            if (dataComponent.isSaddled()) {
                dataComponent.setSaddled(false);
            }
            // если в слоте сбруя - надеваем
        } else if (saddleStack.getItem() instanceof HarnessItem) {
            dataComponent.setSaddled(true);
            // проверяем, кастомная ли
            String customHarnessId = CustomHarnessItem.getCustomHarnessId(saddleStack);
            if (customHarnessId != null) {
                // ставим кастомный id
                dataComponent.setHarnessColor("custom:" + customHarnessId);
            } else {
                // или обычный цвет
                String color = ((HarnessItem) saddleStack.getItem()).getColor();
                dataComponent.setHarnessColor(color);
            }
        }
    }

    // синхронизация инвентаря с состоянием гаста
    public void syncInventoryWithState() {
        // если гаст оседлан, а слот пуст - создаем предмет
        if (dataComponent.isSaddled() && inventory.getItem(0).isEmpty()) {
            String color = dataComponent.getHarnessColor();
            // если сбруя кастомная
            if (color.startsWith("custom:")) {
                String customId = color.substring("custom:".length()); // извлекаем id
                dev.nweaver.happyghastmod.custom.CustomHarnessManager.CustomHarnessData data =
                        dev.nweaver.happyghastmod.custom.CustomHarnessManager.getCustomHarnessData(customId);

                boolean found = false;
                // ищем базовый предмет сбруи
                for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                    if (item instanceof HarnessItem) {
                        // создаем стак и добавляем теги
                        ItemStack tempStack = new ItemStack(item);
                        tempStack.getOrCreateTag().putString("CustomHarnessId", customId);

                        // добавляем имя
                        if (data != null) {
                            net.minecraft.nbt.CompoundTag display = tempStack.getOrCreateTagElement("display");
                            display.putString("Name", "{\"text\":\"" + data.getName() + "\",\"italic\":false}");

                            // добавляем флаги текстур
                            if (data.hasSaddleTexture()) { tempStack.getOrCreateTag().putBoolean("HasCustomSaddleTexture", true); }
                            if (data.hasGlassesTexture()) { tempStack.getOrCreateTag().putBoolean("HasCustomGlassesTexture", true); }
                            if (data.hasAccessoryTexture()) { tempStack.getOrCreateTag().putBoolean("HasCustomAccessoryTexture", true); }
                        }
                        inventory.setItem(0, tempStack);
                        found = true;
                        break;
                    }
                }
                // фоллбэк на случай, если что-то пошло не так
                if (!found) {
                    for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                        if (item instanceof HarnessItem) {
                            ItemStack baseStack = new ItemStack(item);
                            baseStack.getOrCreateTag().putString("CustomHarnessId", customId);
                            inventory.setItem(0, baseStack);
                            break;
                        }
                    }
                }
            } else {
                // если сбруя обычная
                for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                    if (item instanceof HarnessItem harnessItem && harnessItem.getColor().equals(color)) {
                        inventory.setItem(0, new ItemStack(item));
                        break;
                    }
                }
            }
            // если гаст не оседлан, а в слоте что-то есть - чистим
        } else if (!dataComponent.isSaddled() && !inventory.getItem(0).isEmpty()) {
            inventory.setItem(0, ItemStack.EMPTY);
        }
    }


    // сохранение данных в nbt
    public void addAdditionalSaveData(CompoundTag compound) {
        ListTag inventoryTag = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Slot", i);
                stack.save(slotTag); // сохраняем предмет с тегами
                // бэкап id для кастомной сбруи для надежности
                if (stack.getItem() instanceof HarnessItem && CustomHarnessItem.getCustomHarnessId(stack) != null) {
                    slotTag.putString("CustomHarnessBackup", CustomHarnessItem.getCustomHarnessId(stack));
                }
                inventoryTag.add(slotTag);
            }
        }
        compound.put("GhastInventory", inventoryTag);
        // также сохраняем цвет напрямую
        if (dataComponent.isSaddled()) {
            compound.putString("DirectHarnessColor", dataComponent.getHarnessColor());
        }
    }

    // загрузка данных из nbt
    public void readAdditionalSaveData(CompoundTag compound) {
        // грузим инвентарь
        if (compound.contains("GhastInventory", Tag.TAG_LIST)) {
            ListTag inventoryTag = compound.getList("GhastInventory", Tag.TAG_COMPOUND);
            for (int i = 0; i < inventoryTag.size(); i++) {
                CompoundTag slotTag = inventoryTag.getCompound(i);
                int slot = slotTag.getInt("Slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    ItemStack stack = ItemStack.of(slotTag);
                    // восстанавливаем id кастомной сбруи из бэкапа
                    if (slotTag.contains("CustomHarnessBackup") && stack.getItem() instanceof HarnessItem) {
                        if (CustomHarnessItem.getCustomHarnessId(stack) == null) {
                            stack.getOrCreateTag().putString("CustomHarnessId", slotTag.getString("CustomHarnessBackup"));
                        }
                    }
                    inventory.setItem(slot, stack);
                }
            }
        } else {
            // для старых сохранений
            syncInventoryWithState();
        }
        // проверяем прямое сохранение цвета
        if (compound.contains("DirectHarnessColor")) {
            String color = compound.getString("DirectHarnessColor");
            // если что, пытаемся создать предмет по цвету
            if (color.startsWith("custom:") && inventory.getItem(0).isEmpty()) {
                String customId = color.substring("custom:".length());
                for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
                    if (item instanceof HarnessItem) {
                        ItemStack stack = new ItemStack(item);
                        stack.getOrCreateTag().putString("CustomHarnessId", customId);
                        inventory.setItem(0, stack);
                        break;
                    }
                }
            }
        }
    }

    // реализация интерфейса container
    @Override
    public int getContainerSize() { return inventory.getContainerSize(); }

    @Override
    public boolean isEmpty() { return inventory.isEmpty(); }

    @Override
    public ItemStack getItem(int index) { return inventory.getItem(index); }

    @Override
    public ItemStack removeItem(int index, int count) { return inventory.removeItem(index, count); }

    @Override
    public ItemStack removeItemNoUpdate(int index) { return inventory.removeItemNoUpdate(index); }

    @Override
    public void setItem(int index, ItemStack stack) { inventory.setItem(index, stack); }

    @Override
    public void setChanged() { inventory.setChanged(); }

    @Override
    public boolean stillValid(Player player) { return owner.isAlive() && owner.distanceTo(player) < 8.0F && canUseInventory(player); }

    @Override
    public void clearContent() { inventory.clearContent(); }
}