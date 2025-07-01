package dev.nweaver.happyghastmod.container;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.init.ContainerInit;
import dev.nweaver.happyghastmod.item.HarnessItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public class GhastContainer extends AbstractContainerMenu {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Container ghastContainer;
    @Nullable // гаст может быть null, если что-то пошло не так при поиске
    public final HappyGhast ghast;

    // метод для создания контейнера из буфера
    public static GhastContainer create(int windowId, Inventory playerInventory, FriendlyByteBuf data) {
        HappyGhast foundGhast = null;
        Container container = null;

        if (data != null && data.readableBytes() >= 4) {
            try {
                int entityId = data.readInt();

                if (Minecraft.getInstance().level != null) {
                    Entity entity = Minecraft.getInstance().level.getEntity(entityId);
                    if (entity instanceof HappyGhast happyGhast) {
                        foundGhast = happyGhast;

                        // получаем инвентарь найденного гаста
                        if (foundGhast.getInventoryComponent() != null) {
                            container = foundGhast.getInventoryComponent();
                        }
                    } else if (entity != null) {
                        LOGGER.error("Entity with ID {} is not a HappyGhast", entityId);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Exception while reading ghast ID from buffer", e);
            }
        }

        // если не удалось получить инвентарь, используем пустой
        if (container == null) {
            container = new SimpleContainer(1);
        }

        // создаем и возвращаем новый экземпляр контейнера
        return new GhastContainer(windowId, playerInventory, container, foundGhast);
    }

    // основной конструктор
    public GhastContainer(int id, Inventory playerInventory, Container ghastContainer, @Nullable HappyGhast ghast) {
        super(ContainerInit.GHAST_CONTAINER.get(), id);
        this.ghastContainer = ghastContainer;
        this.ghast = ghast;

        // добавляем слот для сбруи
        this.addSlot(new HarnessSlot(this.ghastContainer, 0, 8, 18));

        // добавляем инвентарь игрока
        addPlayerInventorySlots(playerInventory);
    }

    // метод для добавления слотов игрока
    private void addPlayerInventorySlots(Inventory playerInventory) {
        // 3 ряда инвентаря
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        // хотбар
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    // создаем слот специально для сбруи
    private class HarnessSlot extends Slot {
        public HarnessSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof HarnessItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // проверяем, жив ли гаст и является ли игрок пассажиром или находится близко
        return this.ghast != null
                && this.ghast.isAlive()
                && (this.ghast.hasPassenger(player) || player.distanceTo(this.ghast) < 8.0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            result = slotStack.copy();

            if (index < this.ghastContainer.getContainerSize()) {
                // из инвентаря гаста в инвентарь игрока
                if (!this.moveItemStackTo(slotStack, this.ghastContainer.getContainerSize(), this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(slotStack)) {
                // из инвентаря игрока в инвентарь гаста, если это сбруя
                if (!this.moveItemStackTo(slotStack, 0, this.ghastContainer.getContainerSize(), false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return result;
    }
}