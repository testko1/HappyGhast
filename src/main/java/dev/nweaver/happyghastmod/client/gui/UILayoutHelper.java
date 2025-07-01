package dev.nweaver.happyghastmod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// класс для работы с размещением элементов интерфейса и расчетом позиций
public class UILayoutHelper {
    // константы интерфейса
    private static final int MIN_WIDTH = 360;

    // вычисляемые размеры
    private int guiWidth;
    private int guiHeight;
    private int leftX;
    private int topY;

    // размеры и позиции области текстур
    private int textureAreaY;
    private int textureAreaHeight = 70;

    // рассчитывает размеры и позиции элементов интерфейса на основе размера экрана
    public void calculateLayout(int screenWidth, int screenHeight) {
        guiWidth = Math.max(MIN_WIDTH, Math.min(screenWidth - 40, 400));
        guiHeight = Math.min(screenHeight - 40, 280);
        leftX = (screenWidth - guiWidth) / 2;
        topY = (screenHeight - guiHeight) / 2;
        textureAreaY = topY + 90;
    }

    // отрисовывает основную панель интерфейса
    public void renderMainPanel(GuiGraphics graphics, net.minecraft.client.gui.Font font, Component title) {
        // основная панель
        graphics.fill(leftX, topY, leftX + guiWidth, topY + guiHeight, 0xFF404040);
        // рамка
        graphics.fill(leftX, topY, leftX + guiWidth, topY + 2, 0xFF858585);
        graphics.fill(leftX, topY, leftX + 2, topY + guiHeight, 0xFF858585);
        graphics.fill(leftX + guiWidth - 2, topY, leftX + guiWidth, topY + guiHeight, 0xFF858585);
        // заголовок
        graphics.drawCenteredString(font, title, leftX + guiWidth / 2, topY + 4, 0xFFFF00);
    }

    // геттеры для доступа к размерам и позициям
    public int getGuiWidth() {
        return guiWidth;
    }

    public int getGuiHeight() {
        return guiHeight;
    }

    public int getLeftX() {
        return leftX;
    }

    public int getTopY() {
        return topY;
    }

    public int getTextureAreaY() {
        return textureAreaY;
    }

    public int getTextureAreaHeight() {
        return textureAreaHeight;
    }

    // расчет позиций элементов интерфейса
    public int getNameFieldX() {
        return leftX + 70;
    }

    public int getNameFieldY() {
        return topY + 15;
    }

    public int getNameFieldWidth() {
        return 160;
    }

    public int getFolderButtonX() {
        return leftX + 245;
    }

    public int getFolderButtonY() {
        return topY + 15;
    }

    public int getNameLabelX() {
        return leftX + 20;
    }

    public int getNameLabelY() {
        return topY + 18;
    }

    public int getTypeButtonX() {
        return leftX + 15;
    }

    public int getTypeButtonY() {
        return topY + 45;
    }

    public int getTextureButtonX() {
        return leftX + 50;
    }

    public int getTextureButtonY() {
        return topY + 120;
    }

    public int getTextureButtonWidth() {
        return 180;
    }

    public int getTextureButtonHeight() {
        return 36;
    }

    public int getNavButtonY() {
        return topY + 123;
    }

    public int getLeftNavButtonX() {
        return leftX + 15;
    }

    public int getRightNavButtonX() {
        return leftX + 240;
    }

    public int getPreviewX() {
        return leftX + guiWidth - 105;
    }

    public int getPreviewY() {
        return topY + 55;
    }

    public int getPreviewWidth() {
        return 90;
    }

    public int getPreviewHeight() {
        return 90;
    }

    public int getCreateButtonX() {
        return leftX + 40;
    }

    public int getCreateButtonY() {
        return topY + guiHeight - 35;
    }

    public int getCancelButtonX() {
        return leftX + guiWidth - 90;
    }

    public int getCancelButtonY() {
        return topY + guiHeight - 30;
    }

    public int getStatusX() {
        return leftX + guiWidth / 2;
    }

    public int getStatusY() {
        return topY + guiHeight - 50;
    }

    // константы для позиционирования выбранных текстур
    public static final int SELECTED_TEXTURES_OFFSET_X = 140;
    public static final int SELECTED_TEXTURES_OFFSET_Y = 40;
    public static final int TEXTURES_TITLE_OFFSET_X = 5;
    public static final int TEXTURES_TITLE_OFFSET_Y = 5;
    public static final int FIRST_ROW_OFFSET_Y = 20;
    public static final int SECOND_ROW_OFFSET_Y = 35;

    // позиции для текстов в блоке выбранных текстур
    public static final int ITEM_LABEL_OFFSET_X = 10;
    public static final int ITEM_STATUS_OFFSET_X = 40;
    public static final int GLASSES_LABEL_OFFSET_X = 70;
    public static final int GLASSES_STATUS_OFFSET_X = 125;
}