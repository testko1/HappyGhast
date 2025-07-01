package dev.nweaver.happyghastmod.events;

import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.ClientGhastInventoryHandler;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import dev.nweaver.happyghastmod.network.GhastVerticalMovementPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID, value = Dist.CLIENT)
public class KeyHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    // привязка для спуска гаста
    public static final KeyMapping DESCEND_KEY = new KeyMapping(
            "key.happyghastmod.descend",  // ключ для локализации
            GLFW.GLFW_KEY_C,              // c по умолчанию
            "key.categories.happyghastmod" // категория в настройках
    );

    // привязка для подъема
    public static final KeyMapping ASCEND_KEY = new KeyMapping(
            "key.happyghastmod.ascend",   // ключ для локализации
            GLFW.GLFW_KEY_SPACE,          //  пробел по умолчанию
            "key.categories.happyghastmod" // категория в настройках
    );

    private static boolean wasInventoryKeyDown = false;
    private static long lastGhastInventoryTime = 0L;
    private static final long INVENTORY_COOLDOWN = 200; // кулдаун 200мс


    // обработка нажатий клавиш с высоким приоритетом
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity vehicle = mc.player.getVehicle();
        if (vehicle == null) {
            wasInventoryKeyDown = false;
            return;
        }

        // отслеживаем состояние клавиш
        boolean isAscending = ASCEND_KEY.isDown();
        boolean isDescending = DESCEND_KEY.isDown();

        // отправляем пакет с обоими состояниями
        NetworkHandler.sendToServer(new GhastVerticalMovementPacket(isAscending, isDescending));

        // обработка нажатия клавиши инвентаря
        if (mc.options.keyInventory.isDown() && !wasInventoryKeyDown) {
            if (vehicle instanceof HappyGhast ghast) {
                LOGGER.debug("Detecting inventory key press for ghast");

                // отправляем запрос на открытие инвентаря
                ClientGhastInventoryHandler.handleClientKeyInput(ghast);

                // запоминаем время последнего открытия
                lastGhastInventoryTime = System.currentTimeMillis();
            }
        }

        wasInventoryKeyDown = mc.options.keyInventory.isDown();
    }

    // обработчик тика клиента для блокировки инвентаря
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // если инвентарь открыт слишком быстро после инвентаря гаста
        // и мы еще на гасте, закрываем его
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGhastInventoryTime < INVENTORY_COOLDOWN) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen &&
                    mc.player.getVehicle() instanceof HappyGhast) {
                mc.setScreen(null);
            }
        }
    }
}