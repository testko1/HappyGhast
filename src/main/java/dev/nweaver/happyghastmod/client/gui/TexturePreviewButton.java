package dev.nweaver.happyghastmod.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.UUID;

// кнопка с превью текстуры
public class TexturePreviewButton extends Button {
    private final File textureFile;
    private ResourceLocation previewTexture;
    private int textureWidth;
    private int textureHeight;
    private String textureType;
    private String selectedTextureType; // новое поле для хранения выбранного типа

    // создает новую кнопку превью текстуры
    public TexturePreviewButton(File textureFile, int x, int y, int width, int height, OnPress onPress) {
        super(x, y, width, height, Component.literal(textureFile.getName()), onPress, DEFAULT_NARRATION);
        this.textureFile = textureFile;

        // определяем тип текстуры на основе имени файла (для автоопределения)
        String fileName = textureFile.getName().toLowerCase();

        // используем тот же порядок, что и в textureutils, для согласованности
        if (fileName.contains("accessory") || fileName.contains("_acc") ||
                fileName.contains(" acc") || fileName.endsWith("acc") ||
                fileName.contains("_accessory") || fileName.contains(" accessory")) {
            this.textureType = "accessory";
        }
        else if (fileName.contains("glasses") || fileName.contains("glass") ||
                fileName.contains("_glass") || fileName.contains(" glass")) {
            this.textureType = "glasses";
        }
        else if (fileName.contains("saddle") || fileName.contains("_saddle") ||
                fileName.contains(" saddle")) {
            this.textureType = "saddle";
        }
        else {
            // если не подходит под другие типы, по умолчанию считаем это седлом
            this.textureType = "saddle";
        }


        // по умолчанию выбранный тип совпадает с определенным
        this.selectedTextureType = this.textureType;

        // загружаем превью текстуры
        loadPreviewTexture();
    }

    // устанавливает выбранный тип текстуры
    public void setSelectedTextureType(String selectedType) {
        this.selectedTextureType = selectedType;
    }

    // загружает превью текстуры из файла
    private void loadPreviewTexture() {
        try {
            byte[] fileData = Files.readAllBytes(textureFile.toPath());
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(fileData));

            // сохраняем размеры текстуры для правильной отрисовки
            this.textureWidth = nativeImage.getWidth();
            this.textureHeight = nativeImage.getHeight();

            DynamicTexture dynamicTexture = new DynamicTexture(nativeImage);
            this.previewTexture = Minecraft.getInstance().getTextureManager()
                    .register("texture/" + UUID.randomUUID().toString(), dynamicTexture);
        } catch (Exception e) {
        }
    }

    // возвращает файл текстуры
    public File getTextureFile() {
        return textureFile;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // цвет фона зависит от состояния наведения
        final int bgColor = this.isHovered ? 0xFF777777 : 0xFF555555;
        graphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, bgColor);

        if (previewTexture != null) {
            // положение индикатора
            int indicatorX = this.getX() + 10;
            int indicatorY = this.getY() + (this.height - 10) / 2;

            // размер индикатора
            int indicatorSize = 10;

            // проверяем соотношение сторон для выбранного типа
            boolean isValidRatio = isValidAspectRatio(selectedTextureType);

            // цвет индикатора зависит от выбранного типа и соотношения сторон
            int indicatorColor;
            if (isValidRatio) {
                indicatorColor = getColorForTextureType(selectedTextureType);
            } else {
                indicatorColor = 0xFFFF5555; // красный - неверное соотношение
            }

            graphics.fill(indicatorX, indicatorY,
                    indicatorX + indicatorSize,
                    indicatorY + indicatorSize,
                    indicatorColor);

            // обрезаем длинные имена файлов
            String filename = textureFile.getName();
            if (filename.length() > 45) {
                filename = filename.substring(0, 42) + "...";
            }

            // выводим тип текстуры
            String typeLabel = selectedTextureType.substring(0, 1).toUpperCase() + selectedTextureType.substring(1);
            // применяем матричное преобразование для уменьшения шрифта
            com.mojang.blaze3d.vertex.PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.scale(0.65F, 0.65F, 0.65F);

            // вычисляем позицию с учетом масштабирования (делим на масштаб)
            float scaleFactor = 0.65F;
            int scaledX = (int)((this.getX() + 30) / scaleFactor);
            int scaledY = (int)((this.getY() + (this.height - 8) / 2) / scaleFactor);

            graphics.drawString(Minecraft.getInstance().font,
                    Component.literal(typeLabel + ": " + filename),
                    scaledX, scaledY,
                    this.isHovered ? 0xFFFFFF : 0xCCCCCC);

            // восстанавливаем исходное преобразование
            poseStack.popPose();
        } else {
            // сообщение об ошибке, если текстура не загрузилась
            graphics.drawString(Minecraft.getInstance().font, Component.literal("Texture loading error"),
                    this.getX() + 10, this.getY() + (this.height - 10) / 2, 0xFF5555);
        }
    }

    // проверяет, соответствует ли текстура требуемому соотношению сторон
    private boolean isValidAspectRatio(String textureTypeToCheck) {
        double aspectRatio = (double)textureWidth / textureHeight;

        // проверка соотношения сторон в зависимости от типа текстуры
        switch (textureTypeToCheck) {
            case "saddle":
                return Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для седла
            case "glasses":
                return Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для очков
            case "accessory":
                return Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для аксессуаров
            default:
                return false;
        }
    }

    // возвращает цвет маркера для указанного типа текстуры
    private int getColorForTextureType(String textureTypeToColor) {
        return switch (textureTypeToColor) {
            case "saddle" -> 0xFF55FF55;    // зеленый
            case "glasses" -> 0xFF5555FF;   // синий
            case "accessory" -> 0xFFFFFF55; // желтый
            default -> 0xFFFFFFFF;          // белый
        };
    }
}