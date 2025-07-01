package dev.nweaver.happyghastmod.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

// управляет текстурами для кастомной сбруи
public class HarnessTextureManager {
    private final String previewHarnessId;
    // имена текущих выбранных текстур
    private String saddleTextureName = "Not selected";
    private String glassesTextureName = "Not selected";
    private String accessoryTextureName = "Not selected";
    // данные текстур
    private byte[] saddleTextureData;
    private byte[] glassesTextureData;
    private byte[] accessoryTextureData;
    // превью текстур
    private ResourceLocation saddlePreviewTexture;
    private ResourceLocation glassesPreviewTexture;
    private ResourceLocation accessoryPreviewTexture;
    // менеджер статусных сообщений для обратной связи
    private final StatusMessageManager statusManager;

    public HarnessTextureManager(String previewHarnessId) {
        this.previewHarnessId = previewHarnessId;
        this.statusManager = new StatusMessageManager();
    }

    // выбирает текстуру из файла и обновляет соответствующие данные
    public void selectTexture(File textureFile, String textureType) {
        try {
            // загружаем данные текстуры
            byte[] textureData = loadTextureData(textureFile);
            if (textureData == null) {
                statusManager.showStatus(Component.literal("Error loading texture!"), 0xFF5555);
                return;
            }
            // анализируем размеры текстуры
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(textureData));
                int width = image.getWidth();
                int height = image.getHeight();
                // проверяем соотношение для выбранного типа
                boolean validRatio = false;
                switch (textureType) {
                    case "saddle":
                        validRatio = Math.abs((double) width / height - 2.0) < 0.1; // ~2:1
                        break;
                    case "glasses":
                        validRatio = Math.abs((double) width / height - 2.0) < 0.1; // ~2:1
                        break;
                    case "accessory":
                        validRatio = Math.abs((double) width / height - 2.0) < 0.1; // изменено на 2:1
                        break;
                }
                image.close();
                // если соотношение не подходит, но пользователь явно выбрал этот тип,
                // выводим предупреждение, но все равно принимаем текстуру
                if (!validRatio) {
                    statusManager.showStatus(
                            Component.literal("Warning: aspect ratio is not optimal for " + getLocalizedTypeName(textureType)),
                            0xFFFF55);
                }
            } catch (Exception e) {
            }
            // установка выбранной текстуры - принимаем любую
            String shortName = getShortFileName(textureFile);
            switch (textureType) {
                case "saddle":
                    saddleTextureData = textureData;
                    saddlePreviewTexture = createPreviewTexture(textureData);
                    saddleTextureName = shortName;
                    break;
                case "glasses":
                    glassesTextureData = textureData;
                    glassesPreviewTexture = createPreviewTexture(textureData);
                    glassesTextureName = shortName;
                    break;
                case "accessory":
                    accessoryTextureData = textureData;
                    accessoryPreviewTexture = createPreviewTexture(textureData);
                    accessoryTextureName = shortName;
                    break;
            }
            String typeName = getLocalizedTypeName(textureType);
            statusManager.showStatus(Component.literal("Selected texture " + typeName + ": " + shortName), 0x55FF55);
            // вызываем callback для обновления превью
            triggerPreviewUpdate();
        } catch (Exception e) {
            statusManager.showStatus(Component.literal("Error loading texture!"), 0xFF5555);
        }
    }

    public interface PreviewUpdateCallback {
        void onTextureSelected();
    }

    private PreviewUpdateCallback previewUpdateCallback;

    // устанавливает функцию обратного вызова для обновления превью
    public void setPreviewUpdateCallback(PreviewUpdateCallback callback) {
        this.previewUpdateCallback = callback;
    }

    // вызывает функцию обратного вызова для обновления превью
    private void triggerPreviewUpdate() {
        if (previewUpdateCallback != null) {
            previewUpdateCallback.onTextureSelected();
        }
    }

    // возвращает локализованное название типа текстуры
    private String getLocalizedTypeName(String textureType) {
        return switch (textureType) {
            case "saddle" -> "saddle";
            case "glasses" -> "glasses";
            case "accessory" -> "accessory";
            default -> "unknown type";
        };
    }

    // загружает данные текстуры из файла
    private byte[] loadTextureData(File file) {
        try {
            return Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            return null;
        }
    }

    // создает ресурс превью для текстуры
    public ResourceLocation createPreviewTexture(byte[] textureData) {
        try {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(textureData));
            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            return Minecraft.getInstance().getTextureManager()
                    .register("preview/" + UUID.randomUUID().toString(), dynamicTexture);
        } catch (Exception e) {
            return null;
        }
    }

    // обновляет превью сбруи с текущими выбранными текстурами
    public void updatePreviewHarness(String previewName, HappyGhast previewGhast) {
        try {
            if (saddleTextureData != null) validateTextureData(saddleTextureData, "Saddle");
            if (glassesTextureData != null) validateTextureData(glassesTextureData, "Glasses");
            if (accessoryTextureData != null) validateTextureData(accessoryTextureData, "Accessory");
            // регистрируем превью сбруи со всеми доступными текстурами
            dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager.registerPreviewHarness(
                    previewHarnessId, previewName, "Вы",
                    saddleTextureData, glassesTextureData, accessoryTextureData);
            if (previewGhast != null) {
                // применяем превью к гасту
                previewGhast.setSaddled(true);
                previewGhast.setHarnessColor("custom:" + previewHarnessId);
                // принудительно обновляем отображение
                previewGhast.refreshSaddle();
            }
        } catch (Exception e) {
        }
    }

    // возвращает сокращенное имя файла для отображения
    private String getShortFileName(File file) {
        String name = file.getName();
        if (name.length() > 12) {
            return name.substring(0, 10) + "...";
        }
        return name;
    }

    // рендерит блок с выбранными текстурами
    public void renderSelectedTexturesBlock(GuiGraphics graphics, UILayoutHelper layout, net.minecraft.client.gui.Font font) {
        int baseX = layout.getLeftX() + UILayoutHelper.SELECTED_TEXTURES_OFFSET_X;
        int baseY = layout.getTopY() + UILayoutHelper.SELECTED_TEXTURES_OFFSET_Y;
        // заголовок
        graphics.drawString(font, Component.literal("Selected Textures:"),
                baseX + UILayoutHelper.TEXTURES_TITLE_OFFSET_X,
                baseY + UILayoutHelper.TEXTURES_TITLE_OFFSET_Y, 0xFFFFFF);
        // первый ряд (седло и очки)
        int firstRowY = baseY + UILayoutHelper.FIRST_ROW_OFFSET_Y;
        // седло
        int saddleLabelX = baseX + UILayoutHelper.ITEM_LABEL_OFFSET_X; // используем позицию item
        int saddleStatusX = baseX + UILayoutHelper.ITEM_STATUS_OFFSET_X; // используем позицию item
        graphics.drawString(font, Component.literal("Saddle:"), saddleLabelX, firstRowY, 0xFFFFFF);
        renderCompactTextureInfo(graphics, saddleStatusX, firstRowY, saddleTextureData, false); // седло не обязательно
        // очки
        int glassesLabelX = baseX + UILayoutHelper.GLASSES_LABEL_OFFSET_X;
        int glassesStatusX = baseX + UILayoutHelper.GLASSES_STATUS_OFFSET_X;
        graphics.drawString(font, Component.literal("Glasses:"), glassesLabelX, firstRowY, 0xFFFFFF);
        renderCompactTextureInfo(graphics, glassesStatusX, firstRowY, glassesTextureData, false); // очки не обязательны

        // второй ряд (аксессуар)
        int secondRowY = baseY + UILayoutHelper.SECOND_ROW_OFFSET_Y;
        // аксессуар
        int accLabelX = baseX + UILayoutHelper.ITEM_LABEL_OFFSET_X; // используем позицию item
        int accStatusX = baseX + UILayoutHelper.ITEM_STATUS_OFFSET_X; // используем позицию item
        graphics.drawString(font, Component.literal("Accessory:"), accLabelX, secondRowY, 0xFFFFFF);
        renderCompactTextureInfo(graphics, accStatusX, secondRowY, accessoryTextureData, false); // аксессуар не обязателен
    }

    // рендерит информацию о статусе текстуры (выбрана/не выбрана)
    private void renderCompactTextureInfo(GuiGraphics graphics, int x, int y, byte[] textureData, boolean required) {
        if (textureData != null) {
            // текстура выбрана - отображаем галочку
            graphics.drawString(Minecraft.getInstance().font, Component.literal("✓"), x, y, 0x55FF55);
        } else {
            // текстура не выбрана
            String text = "X";
            int color = 0xAAAAAA;
            graphics.drawString(Minecraft.getInstance().font, Component.literal(text), x, y, color);
        }
    }

    // геттеры для доступа к данным текстур
    public byte[] getSaddleTextureData() {
        return saddleTextureData;
    }

    public byte[] getGlassesTextureData() {
        return glassesTextureData;
    }

    public byte[] getAccessoryTextureData() {
        return accessoryTextureData;
    }

    private void validateTextureData(byte[] textureData, String textureType) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(textureData));
            int width = image.getWidth();
            int height = image.getHeight();
            image.close();
        } catch (Exception e) {
        }
    }
}