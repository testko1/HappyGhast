package dev.nweaver.happyghastmod.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nweaver.happyghastmod.HappyGhastMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side manager for custom harnesses.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = HappyGhastMod.MODID, value = Dist.CLIENT)
public class ClientCustomHarnessManager {

    private static final Logger LOGGER = LogManager.getLogger();
    // Maps for custom harness data and textures
    private static final Map<String, HarnessInfo> customHarnessInfo = new HashMap<>();
    private static final Map<String, Map<TextureType, ResourceLocation>> customHarnessTextures = new HashMap<>();

    /**
     * Harness information class.
     */
    public static class HarnessInfo {
        private final String id;
        private final String name;
        private final String creatorName;

        public HarnessInfo(String id, String name, String creatorName) {
            this.id = id;
            this.name = name;
            this.creatorName = creatorName;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCreatorName() {
            return creatorName;
        }
    }

    /**
     * Texture types for custom harnesses.
     */
    public enum TextureType {
        // --- УДАЛЕНО: ITEM ---
        // ITEM,
        SADDLE,
        GLASSES,
        ACCESSORY
    }

    /**
     * Clears all custom harness data when disconnecting from a server.
     */
    @SubscribeEvent
    public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        clearCustomHarnesses();
    }

    /**
     * Registers a custom harness on the client side.
     */
    public static void registerCustomHarness(String harnessId, String harnessName, String creatorName,
                                             // --- УДАЛЕНО: itemTextureData ---
                                             // byte[] itemTextureData,
                                             byte[] saddleTextureData,
                                             byte[] glassesTextureData, byte[] accessoryTextureData) {
        customHarnessInfo.put(harnessId, new HarnessInfo(harnessId, harnessName, creatorName));
        Map<TextureType, ResourceLocation> textureMap = new HashMap<>();
        // --- УДАЛЕНО: Логика для itemTexture ---
        // ResourceLocation itemTexture = registerTexture(harnessId, TextureType.ITEM, itemTextureData);
        // if (itemTexture != null) {
        //     textureMap.put(TextureType.ITEM, itemTexture);
        // }
        if (saddleTextureData != null) {
            ResourceLocation saddleTexture = registerTexture(harnessId, TextureType.SADDLE, saddleTextureData);
            if (saddleTexture != null) {
                textureMap.put(TextureType.SADDLE, saddleTexture);
            }
        }
        if (glassesTextureData != null) {
            ResourceLocation glassesTexture = registerTexture(harnessId, TextureType.GLASSES, glassesTextureData);
            if (glassesTexture != null) {
                textureMap.put(TextureType.GLASSES, glassesTexture);
            }
        }
        if (accessoryTextureData != null) {
            ResourceLocation accessoryTexture = registerTexture(harnessId, TextureType.ACCESSORY, accessoryTextureData);
            if (accessoryTexture != null) {
                textureMap.put(TextureType.ACCESSORY, accessoryTexture);
            }
        }
        customHarnessTextures.put(harnessId, textureMap);
    }

    /**
     * Registers a texture and returns its ResourceLocation.
     */
    private static ResourceLocation registerTexture(String harnessId, TextureType type, byte[] textureData) {
        try {
            // --- ИЗМЕНЕНО: Проверка на null ---
            if (textureData == null || textureData.length == 0) {
                LOGGER.warn("Attempted to register null or empty texture data for harness {} type {}", harnessId, type);
                return null; // Не регистрируем пустую текстуру
            }

            // Создаем изображение из байтов
            NativeImage image = NativeImage.read(new ByteArrayInputStream(textureData));

            // Создаем уникальный идентификатор для текстуры
            ResourceLocation location = HappyGhastMod.rl(
                    "textures/dynamic/" + harnessId + "_" + type.name().toLowerCase() + "_" + System.currentTimeMillis()
            );

            // Создаем динамическую текстуру и регистрируем её
            DynamicTexture texture = new DynamicTexture(image);

            // Используем синхронную регистрацию через Minecraft.getInstance()
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getTextureManager().register(location, texture);
            });

            return location;
        } catch (IOException e) {
            LOGGER.error("Failed to register custom harness texture: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Registers a preview harness on the client side.
     */
    public static void registerPreviewHarness(String harnessId, String harnessName, String creatorName,
                                              // --- УДАЛЕНО: itemTextureData ---
                                              // byte[] itemTextureData,
                                              byte[] saddleTextureData,
                                              byte[] glassesTextureData, byte[] accessoryTextureData) {
        try {
            customHarnessInfo.put(harnessId, new HarnessInfo(harnessId, harnessName, creatorName));
            Map<TextureType, ResourceLocation> textureMap = new HashMap<>();

            // --- УДАЛЕНО: Логика для itemTexture ---
            // if (itemTextureData != null) {
            //     ResourceLocation itemTexture = registerTexture(harnessId, TextureType.ITEM, itemTextureData);
            //     if (itemTexture != null) {
            //         textureMap.put(TextureType.ITEM, itemTexture);
            //     }
            // } else {
            //     // Use default if null for preview
            //     textureMap.put(TextureType.ITEM, getDefaultTextureForType(TextureType.ITEM));
            // }


            if (saddleTextureData != null) {
                ResourceLocation saddleTexture = registerTexture(harnessId, TextureType.SADDLE, saddleTextureData);
                if (saddleTexture != null) {
                    textureMap.put(TextureType.SADDLE, saddleTexture);
                }
            } else {
                // Use default if null for preview
                textureMap.put(TextureType.SADDLE, getDefaultTextureForType(TextureType.SADDLE));
            }

            if (glassesTextureData != null) {
                ResourceLocation glassesTexture = registerTexture(harnessId, TextureType.GLASSES, glassesTextureData);
                if (glassesTexture != null) {
                    textureMap.put(TextureType.GLASSES, glassesTexture);
                }
            } else {
                // Use default if null for preview
                textureMap.put(TextureType.GLASSES, getDefaultTextureForType(TextureType.GLASSES));
            }


            if (accessoryTextureData != null) {
                ResourceLocation accessoryTexture = registerTexture(harnessId, TextureType.ACCESSORY, accessoryTextureData);
                if (accessoryTexture != null) {
                    textureMap.put(TextureType.ACCESSORY, accessoryTexture);
                }
            } else {
                // Use default if null for preview
                textureMap.put(TextureType.ACCESSORY, getDefaultTextureForType(TextureType.ACCESSORY));
            }


            customHarnessTextures.put(harnessId, textureMap);
        } catch (Exception e) {
            LOGGER.error("Error in registerPreviewHarness: {}", e.getMessage());
        }
    }

    private static ResourceLocation getDefaultTextureForType(TextureType type) {
        switch (type) {
            // --- УДАЛЕНО: ITEM ---
            // case ITEM:
            //     return HappyGhastMod.rl("textures/items/blue_harness.png"); // Example default item texture
            case SADDLE:
                return HappyGhastMod.rl("textures/entity/ghastsaddleblue.png");
            case GLASSES:
                return HappyGhastMod.rl("textures/entity/glassestexture.png");
            case ACCESSORY:
                // Assuming a default accessory texture exists or return null/another default
                return HappyGhastMod.rl("textures/entity/glasses_accessory.png"); // Example default
            default:
                // Fallback, maybe return saddle texture?
                return HappyGhastMod.rl("textures/entity/ghastsaddleblue.png");
        }
    }


    /**
     * Clears all custom harnesses.
     */
    public static void clearCustomHarnesses() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (Map<TextureType, ResourceLocation> textureMap : customHarnessTextures.values()) {
            for (ResourceLocation location : textureMap.values()) {
                // Check if the texture location is not one of the default ones before releasing
                if (!isDefaultTexture(location)) {
                    AbstractTexture texture = textureManager.getTexture(location, null); // Use getTexture with default
                    if (texture != null) {
                        texture.close(); // Close the texture resource
                        // Release only if it's a dynamic texture managed by us
                        if (texture instanceof DynamicTexture) {
                            Minecraft.getInstance().execute(() -> textureManager.release(location));
                        }
                    }
                }
            }
        }
        customHarnessInfo.clear();
        customHarnessTextures.clear();
    }

    // Helper to check if a texture is one of the defaults
    private static boolean isDefaultTexture(ResourceLocation location) {
        return location.equals(getDefaultTextureForType(TextureType.SADDLE)) ||
                location.equals(getDefaultTextureForType(TextureType.GLASSES)) ||
                location.equals(getDefaultTextureForType(TextureType.ACCESSORY));
        // Item type removed
    }


    /**
     * Gets custom harness info by ID.
     */
    public static HarnessInfo getCustomHarnessInfo(String harnessId) {
        return customHarnessInfo.get(harnessId);
    }

    /**
     * Gets a custom harness texture by ID and type.
     */
    public static ResourceLocation getHarnessTexture(String harnessId, TextureType type) {
        // --- УДАЛЕНО: Проверка на ITEM ---
        // if (type == TextureType.ITEM) {
        //     LOGGER.warn("Attempted to get ITEM texture for custom harness {}, which is not supported.", harnessId);
        //     return getDefaultTextureForType(TextureType.ITEM); // Return default item texture
        // }

        Map<TextureType, ResourceLocation> textureMap = customHarnessTextures.get(harnessId);
        // --- ИЗМЕНЕНО: Возвращаем текстуру по умолчанию для типа, если кастомная не найдена ---
        return textureMap == null ? getDefaultTextureForType(type) : textureMap.getOrDefault(type, getDefaultTextureForType(type));
    }

    /**
     * Checks if a harness has a specific texture type.
     */
    public static boolean hasCustomTexture(String harnessId, TextureType type) {
        // --- УДАЛЕНО: Проверка на ITEM ---
        // if (type == TextureType.ITEM) return false; // Item textures are not custom

        Map<TextureType, ResourceLocation> textureMap = customHarnessTextures.get(harnessId);
        return textureMap != null && textureMap.containsKey(type) && !textureMap.get(type).equals(getDefaultTextureForType(type));
    }


    /**
     * Gets all registered custom harness IDs.
     */
    public static Map<String, HarnessInfo> getAllCustomHarnesses() {
        return new HashMap<>(customHarnessInfo);
    }

    /**
     * Unregisters a preview harness and releases its textures.
     */
    public static void unregisterPreviewHarness(String harnessId) {
        customHarnessInfo.remove(harnessId);
        Map<TextureType, ResourceLocation> textureMap = customHarnessTextures.remove(harnessId);
        if (textureMap != null) {
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation location : textureMap.values()) {
                // --- ИЗМЕНЕНО: Проверяем, что это не дефолтная текстура перед удалением ---
                if (!isDefaultTexture(location)) {
                    AbstractTexture texture = textureManager.getTexture(location, null);
                    if (texture != null) {
                        texture.close();
                        if (texture instanceof DynamicTexture) {
                            Minecraft.getInstance().execute(() -> textureManager.release(location));
                        }
                    }
                }
            }
        }
    }
}