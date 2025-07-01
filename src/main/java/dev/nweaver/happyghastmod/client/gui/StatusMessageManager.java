package dev.nweaver.happyghastmod.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

// управляет отображением статусных сообщений в интерфейсе
public class StatusMessageManager {
    // сообщение статуса
    private Component statusMessage = Component.empty();

    // время отображения сообщения
    private int statusMessageTimer = 0;

    // цвет сообщения
    private int statusMessageColor = 0xFFFFFF;

    // устанавливает новое сообщение статуса для отображения
    public void showStatus(Component message, int color) {
        statusMessage = message;
        statusMessageTimer = 100; // отображать 5 секунд
        statusMessageColor = color;
    }

    // рендерит текущее сообщение статуса, если оно активно
    public void renderStatusMessage(GuiGraphics graphics, net.minecraft.client.gui.Font font, int x, int y) {
        if (statusMessageTimer > 0) {
            graphics.drawCenteredString(font, statusMessage, x, y, statusMessageColor);
            statusMessageTimer--;
        }
    }
}