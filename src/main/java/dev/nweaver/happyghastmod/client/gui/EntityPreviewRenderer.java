package dev.nweaver.happyghastmod.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nweaver.happyghastmod.entity.HappyGhast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

// класс для рендеринга превью-гаста
public class EntityPreviewRenderer {
    // превью модель
    private final HappyGhast previewEntity;

    // поворот модели
    private float rotationX = 0;
    private float rotationY = 0;

    // параметры для настройки размера и положения модели
    private float modelScale = 5.0F;
    private float modelY = 0.0F;
    private float modelZ = 30.0F;

    // для обработки перетаскивания мышью
    private boolean isRotating = false;
    private int lastMouseX;
    private int lastMouseY;

    // создает новый рендер-превью с гастом
    public EntityPreviewRenderer(HappyGhast previewEntity) {
        this.previewEntity = previewEntity;
    }

    // рендерит превью-гаста в указанной области
    public void renderPreview(GuiGraphics graphics, int previewX, int previewY,
                              int previewWidth, int previewHeight, net.minecraft.client.gui.Font font) {
        if (previewEntity == null) {
            return;
        }

        // рисуем фон и рамку области превью
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF202020);
        graphics.fill(previewX, previewY, previewX + previewWidth, previewY + 2, 0xFF555555);
        graphics.fill(previewX, previewY + previewHeight - 2, previewX + previewWidth, previewY + previewHeight, 0xFF555555);
        graphics.fill(previewX, previewY, previewX + 2, previewY + previewHeight, 0xFF555555);
        graphics.fill(previewX + previewWidth - 2, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF555555);

        // рисуем заголовок
        graphics.drawCenteredString(font, Component.literal("Preview"),
                previewX + previewWidth / 2, previewY - 10, 0xFFFFFF);

        // рендерим гаста
        renderEntityInArea(graphics, previewX + previewWidth / 2, previewY + previewHeight / 2 + 15);
    }

    // рендерит гаста сущность в указанной позиции
    private void renderEntityInArea(GuiGraphics guiGraphics, int posX, int posY) {
        try {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();
            poseStack.translate(posX, posY, modelZ);
            poseStack.scale(modelScale, modelScale, modelScale);

            // сохраняем оригинальные значения поворота
            float originalYRot = previewEntity.getYRot();
            float originalXRot = previewEntity.getXRot();
            float originalYHeadRot = previewEntity.yHeadRot;
            float originalYBodyRot = previewEntity.yBodyRot;

            // сбрасываем повороты для корректного рендеринга
            previewEntity.setYRot(0);
            previewEntity.setXRot(0);
            previewEntity.yHeadRot = 0;
            previewEntity.yBodyRot = 0;

            // применяем повороты для превью
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(rotationY));
            poseStack.mulPose(Axis.XP.rotationDegrees(rotationX));
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            poseStack.translate(0.0D, modelY, 0.0D);

            // настраиваем освещение
            Lighting.setupForEntityInInventory();

            // получаем рендер сущностей
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.overrideCameraOrientation(new Quaternionf());
            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            dispatcher.setRenderShadow(false);

            // рендерим сущность
            RenderSystem.runAsFancy(() -> {
                dispatcher.render(previewEntity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, bufferSource, 15728880);
            });

            bufferSource.endBatch();
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();

            // восстанавливаем оригинальные значения поворота
            previewEntity.setYRot(originalYRot);
            previewEntity.setXRot(originalXRot);
            previewEntity.yHeadRot = originalYHeadRot;
            previewEntity.yBodyRot = originalYBodyRot;

            poseStack.popPose();
        } catch (Exception e) {
        }
    }

    // проверяет, находится ли указанная позиция в области превью
    public boolean isInPreviewArea(double mouseX, double mouseY, int previewX, int previewY,
                                   int previewWidth, int previewHeight) {
        return mouseX >= previewX && mouseX <= previewX + previewWidth &&
                mouseY >= previewY && mouseY <= previewY + previewHeight;
    }

    // начинает вращение модели
    public void startRotating(int mouseX, int mouseY) {
        isRotating = true;
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // останавливает вращение модели
    public void stopRotating() {
        isRotating = false;
    }

    // обновляет вращение модели на основе текущей позиции мыши
    public void updateRotation(int mouseX, int mouseY) {
        rotationY += (mouseX - lastMouseX) * 2.0F;
        rotationX += (mouseY - lastMouseY) * 2.0F;

        // ограничиваем поворот по оси x
        rotationX = Mth.clamp(rotationX, -30.0F, 30.0F);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    // вращается ли модель в данный момент
    public boolean isRotating() {
        return isRotating;
    }

    // возвращает превью-гаста
    public HappyGhast getPreviewEntity() {
        return previewEntity;
    }
}