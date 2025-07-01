package dev.nweaver.happyghastmod.custom;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.init.ItemInit;
import dev.nweaver.happyghastmod.item.HarnessItem;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import dev.nweaver.happyghastmod.network.SyncCustomHarnessPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

// управляет кастомными сбруями, их хранением и распространением
@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID)
public class CustomHarnessManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CUSTOM_HARNESSES_DIR = "happyghastmod/custom_harnesses";
    private static final String REGISTRY_FILE = "registry.json";

    // map зарегистрированных кастомных сбруй (айди -> данные)
    private static final Map<String, CustomHarnessData> registeredHarnesses = new HashMap<>();

    // проверяет, является ли сервер одиночным
    // кастомные сбруи пока работают только в одиночной игре
    public static boolean isSinglePlayerServer(MinecraftServer server) {
        return server != null && server.isSingleplayer();
    }

    // класс данных кастомной сбруи
    public static class CustomHarnessData {
        private final String id;
        private final String name;
        private final String creatorUuid;
        private final String creatorName;
        private final boolean hasSaddleTexture;
        private final boolean hasGlassesTexture;
        private final boolean hasAccessoryTexture;

        public CustomHarnessData(String id, String name, String creatorUuid, String creatorName,
                                 boolean hasSaddleTexture, boolean hasGlassesTexture, boolean hasAccessoryTexture) {
            this.id = id;
            this.name = name;
            this.creatorUuid = creatorUuid;
            this.creatorName = creatorName;
            this.hasSaddleTexture = hasSaddleTexture;
            this.hasGlassesTexture = hasGlassesTexture;
            this.hasAccessoryTexture = hasAccessoryTexture;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCreatorUuid() {
            return creatorUuid;
        }

        public String getCreatorName() {
            return creatorName;
        }

        public boolean hasSaddleTexture() {
            return hasSaddleTexture;
        }

        public boolean hasGlassesTexture() {
            return hasGlassesTexture;
        }

        public boolean hasAccessoryTexture() {
            return hasAccessoryTexture;
        }
    }

    // инициализирует систему кастомных сбруй при старте сервера
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // проверяем, что это одиночная игра
        if (isSinglePlayerServer(event.getServer())) {
            // загружаем зарегистрированные сбруи только в одиночной игре
            loadRegistry();
        } else {
            LOGGER.info("Custom harnesses are currently disabled in multiplayer. They will be loaded in single-player only");
        }
    }

    // регистрирует новую кастомную сбрую
    public static boolean registerCustomHarness(String harnessId, String harnessName,
                                                byte[] saddleTextureData,
                                                byte[] glassesTextureData, byte[] accessoryTextureData,
                                                ServerPlayer player) {
        try {
            // проверяем, что это одиночная игра
            if (!isSinglePlayerServer(player.getServer())) {
                LOGGER.info("Custom harnesses are currently disabled in multiplayer. Player {} attempted to create a harness",
                        player.getName().getString());
                return false;
            }

            // создаем структуру директорий
            Path baseDir = FMLPaths.GAMEDIR.get().resolve(CUSTOM_HARNESSES_DIR);
            Path harnessDir = baseDir.resolve(harnessId);
            Files.createDirectories(harnessDir);

            boolean hasSaddleTexture = false;
            boolean hasGlassesTexture = false;
            boolean hasAccessoryTexture = false;

            if (saddleTextureData != null) {
                Files.write(harnessDir.resolve("saddle.png"), saddleTextureData);
                hasSaddleTexture = true;
            }

            if (glassesTextureData != null) {
                Files.write(harnessDir.resolve("glasses.png"), glassesTextureData);
                hasGlassesTexture = true;
            }

            if (accessoryTextureData != null) {
                Files.write(harnessDir.resolve("accessory.png"), accessoryTextureData);
                hasAccessoryTexture = true;
            }

            // создаем данные сбруи
            CustomHarnessData data = new CustomHarnessData(
                    harnessId,
                    harnessName,
                    player.getStringUUID(),
                    player.getName().getString(),
                    hasSaddleTexture,
                    hasGlassesTexture,
                    hasAccessoryTexture
            );

            // добавляем в реестр
            registeredHarnesses.put(harnessId, data);

            // сохраняем реестр
            saveRegistry();

            // синхронизируем со всеми игроками
            syncCustomHarnessToAllPlayers(data,
                    saddleTextureData,
                    glassesTextureData,
                    accessoryTextureData);

            return true;
        } catch (IOException e) {
            LOGGER.error("Failed to register custom harness: {}", e.getMessage());
            return false;
        }
    }

    public static void syncAllHarnessesToPlayer(ServerPlayer player) {
        // проверяем, что это одиночная игра
        if (!isSinglePlayerServer(player.getServer())) {
            LOGGER.debug("Skipping custom harness sync in multiplayer for player {}", player.getName().getString());
            return;
        }

        // синхронизируем каждую кастомную сбрую с игроком
        for (Map.Entry<String, CustomHarnessData> entry : registeredHarnesses.entrySet()) {
            String harnessId = entry.getKey();
            CustomHarnessData data = entry.getValue();

            // загружаем текстуры
            byte[] saddleTextureData = loadCustomHarnessTexture(harnessId, "saddle");
            byte[] glassesTextureData = loadCustomHarnessTexture(harnessId, "glasses");
            byte[] accessoryTextureData = loadCustomHarnessTexture(harnessId, "accessory");

            // создаем пакет для каждой сбруи
            SyncCustomHarnessPacket packet = new SyncCustomHarnessPacket(
                    data.getId(),
                    data.getName(),
                    data.getCreatorName(),
                    saddleTextureData,
                    glassesTextureData,
                    accessoryTextureData
            );

            // отправляем только этому игроку
            NetworkHandler.sendToPlayer(packet, player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MinecraftServer server = player.getServer();
            // синхронизируем все кастомные сбруи с игроком при входе
            // только в одиночной игре
            if (server != null) {
                if (isSinglePlayerServer(server)) {
                    syncAllHarnessesToPlayer(player);
                } else {
                    // показываем сообщение, что кастомные сбруи доступны только в одиночной игре
                    // только при входе игрока, но не при смене измерений
                    player.sendSystemMessage(Component.literal(
                            "Custom harness creation is currently only available in single-player mode"
                    ));
                }
            }
        }
    }

    // выдает предмет кастомной сбруи игроку
    public static void giveCustomHarnessToPlayer(String harnessId, ServerPlayer player) {
        // проверяем, что это одиночная игра
        if (!isSinglePlayerServer(player.getServer())) {
            LOGGER.info("Cannot give custom harness in multiplayer to player {}", player.getName().getString());
            return;
        }

        CustomHarnessData data = registeredHarnesses.get(harnessId);
        if (data == null) {
            LOGGER.error("Attempted to give non-existent harness with ID {}", harnessId);
            return;
        }

        // ищем базовый предмет сбруи для использования
        Item baseItem = null;
        for (RegistryObject<HarnessItem> harnessRegObj : ItemInit.HARNESS_ITEMS.values()) {
            Item item = harnessRegObj.get();
            // используем первый попавшийся harnessitem как базовый
            if (item instanceof HarnessItem) {
                baseItem = item;
                break;
            }
        }

        if (baseItem == null) {
            LOGGER.error("Base harness item not found for creating custom harness");
            return;
        }

        // создаем стак предмета
        ItemStack stack = new ItemStack(baseItem);

        // добавляем кастомные данные
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("CustomHarnessId", harnessId);
        tag.putString("CustomHarnessName", data.getName()); // добавляем имя сюда

        // при необходимости добавляем флаги для текстур
        if (data.hasSaddleTexture()) {
            tag.putBoolean("HasCustomSaddleTexture", true);
        }
        if (data.hasGlassesTexture()) {
            tag.putBoolean("HasCustomGlassesTexture", true);
        }
        if (data.hasAccessoryTexture()) {
            tag.putBoolean("HasCustomAccessoryTexture", true);
        }

        // добавляем отображаемое имя (теперь из customharnessname)
        CompoundTag display = new CompoundTag();
        display.putString("Name", "{\"text\":\"" + data.getName() + "\",\"italic\":false}");
        tag.put("display", display);

        // выдаем игроку
        player.getInventory().add(stack);
    }


    // загружает реестр кастомных сбруй с диска
    private static void loadRegistry() {
        registeredHarnesses.clear();

        File registryFile = FMLPaths.GAMEDIR.get()
                .resolve(CUSTOM_HARNESSES_DIR)
                .resolve(REGISTRY_FILE)
                .toFile();

        if (!registryFile.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(registryFile)) {
            JsonObject registry = GSON.fromJson(reader, JsonObject.class);

            registry.entrySet().forEach(entry -> {
                String id = entry.getKey();
                JsonObject obj = entry.getValue().getAsJsonObject();

                CustomHarnessData data = new CustomHarnessData(
                        id,
                        obj.get("name").getAsString(),
                        obj.get("creatorUuid").getAsString(),
                        obj.get("creatorName").getAsString(),
                        obj.get("hasSaddleTexture").getAsBoolean(),
                        obj.get("hasGlassesTexture").getAsBoolean(),
                        obj.get("hasAccessoryTexture").getAsBoolean()
                );

                registeredHarnesses.put(id, data);
            });
        } catch (Exception e) {
            LOGGER.error("Failed to load custom harness registry: {}", e.getMessage());
        }
    }

    // сохраняет реестр кастомных сбруй на диск
    private static void saveRegistry() {
        File registryFile = FMLPaths.GAMEDIR.get()
                .resolve(CUSTOM_HARNESSES_DIR)
                .resolve(REGISTRY_FILE)
                .toFile();

        try {
            // при необходимости создаем родительскую директорию
            if (!registryFile.getParentFile().exists()) {
                registryFile.getParentFile().mkdirs();
            }

            JsonObject registry = new JsonObject();

            for (Map.Entry<String, CustomHarnessData> entry : registeredHarnesses.entrySet()) {
                JsonObject obj = new JsonObject();
                CustomHarnessData data = entry.getValue();

                obj.addProperty("name", data.getName());
                obj.addProperty("creatorUuid", data.getCreatorUuid());
                obj.addProperty("creatorName", data.getCreatorName());
                obj.addProperty("hasSaddleTexture", data.hasSaddleTexture());
                obj.addProperty("hasGlassesTexture", data.hasGlassesTexture());
                obj.addProperty("hasAccessoryTexture", data.hasAccessoryTexture());

                registry.add(entry.getKey(), obj);
            }

            try (FileWriter writer = new FileWriter(registryFile)) {
                GSON.toJson(registry, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save custom harness registry: {}", e.getMessage());
        }
    }

    // синхронизирует кастомную сбрую со всеми игроками
    private static void syncCustomHarnessToAllPlayers(CustomHarnessData data,
                                                      byte[] saddleTextureData,
                                                      byte[] glassesTextureData,
                                                      byte[] accessoryTextureData) {
        // создаем пакет
        SyncCustomHarnessPacket packet = new SyncCustomHarnessPacket(
                data.getId(),
                data.getName(),
                data.getCreatorName(),
                saddleTextureData,
                glassesTextureData,
                accessoryTextureData
        );

        // отправляем всем игрокам
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }


    // получает данные кастомной сбруи по айди
    public static CustomHarnessData getCustomHarnessData(String id) {
        return registeredHarnesses.get(id);
    }

    // загружает данные текстуры для кастомной сбруи
    public static byte[] loadCustomHarnessTexture(String harnessId, String textureType) {
        // запрещаем загрузку типа "item"
        if ("item".equals(textureType)) {
            LOGGER.warn("Attempted to load 'item' texture for custom harness {}, which is not supported", harnessId);
            return null;
        }

        Path path = FMLPaths.GAMEDIR.get()
                .resolve(CUSTOM_HARNESSES_DIR)
                .resolve(harnessId)
                .resolve(textureType + ".png");

        if (!Files.exists(path)) {
            return null;
        }

        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            LOGGER.error("Failed to load custom harness texture: {}", e.getMessage());
            return null;
        }
    }
}