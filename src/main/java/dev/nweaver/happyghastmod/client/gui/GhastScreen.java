package dev.nweaver.happyghastmod.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import dev.nweaver.happyghastmod.container.GhastContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// экран инвентаря счастливого гаста
public class GhastScreen extends AbstractContainerScreen<GhastContainer> {
    private static final Logger LOGGER = LogManager.getLogger();

    // оставляем оригинальные resourcelocation
    private static final ResourceLocation GHAST_INVENTORY_LOCATION =
            new ResourceLocation("minecraft", "textures/gui/container/horse.png");

    private float mouseX;
    private float mouseY;

    // параметры для настройки размера и положения модели
    private float modelScale = 6.0F;     // уменьшенный размер
    private float modelY = 0.0F;         // вертикальное смещение
    private float modelZ = 30.0F;        // глубина z модели

    // параметры для плавного следования за курсором
    private static final float MAX_Y_ROTATION = 30.0F;  // увеличенный угол поворота влево/вправо
    private static final float MAX_X_ROTATION = 15.0F;  // максимальный угол поворота вверх/вниз

    public GhastScreen(GhastContainer container, Inventory playerInventory, Component title) {
        super(container, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int pMouseX, int pMouseY) {
        this.mouseX = pMouseX;
        this.mouseY = pMouseY;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // рисуем фон инвентаря (используем текстуру лошади как основу)
        guiGraphics.blit(GHAST_INVENTORY_LOCATION, x, y, 0, 0, this.imageWidth, this.imageHeight);

        // если гаст найден, рендерим его модель
        if (this.menu.ghast != null) {
            try {
                // рендерим модель гаста в левой части интерфейса
                renderEntityInInventory(guiGraphics, x + 51, y + 60, this.menu.ghast);
            } catch (Exception e) {
                LOGGER.error("Failed to render 3D model: {}", e.getMessage());
            }

            // удалено отображение информации о седле и цвете
        } else {
            // сообщение об ошибке, если гаст не найден
            Component errorMessage = Component.literal("Error: Ghast not found!");
            int errorX = x + (this.imageWidth - this.font.width(errorMessage)) / 2;
            guiGraphics.drawString(this.font, errorMessage, errorX, y + 45, 0xFFFF0000);

            // дополнительная информация
            Component additionalInfo = Component.literal("Try again to open the inventory");
            int infoX = x + (this.imageWidth - this.font.width(additionalInfo)) / 2;
            guiGraphics.drawString(this.font, additionalInfo, infoX, y + 60, 0xFFFFAA00);
        }
    }

    // рендерит гаста в инвентаре стандартным способом (как у лошади или ламы)
    private void renderEntityInInventory(GuiGraphics guiGraphics, int posX, int posY, LivingEntity entity) {
        try {
            PoseStack poseStack = guiGraphics.pose();
            poseStack.pushPose();

            // устанавливаем позицию
            poseStack.translate(posX, posY, modelZ);

            // применяем масштаб - для гаста используем меньший масштаб
            poseStack.scale(modelScale, modelScale, modelScale);

            // сохраняем оригинальные значения поворотов сущности
            float originalYRot = entity.getYRot();
            float originalXRot = entity.getXRot();
            float originalYHeadRot = entity.yHeadRot;
            float originalYBodyRot = entity.yBodyRot;

            // сбрасываем все углы поворота сущности перед рендерингом
            entity.setYRot(0);
            entity.setXRot(0);
            entity.yHeadRot = 0;
            entity.yBodyRot = 0;

            // вычисляем поворот на основе положения курсора
            float mousePosX = this.mouseX - posX;
            float mousePosY = this.mouseY - posY;

            // ограничиваем и масштабируем углы поворота
            float angleY = Mth.clamp((float)Math.atan(mousePosX / 40.0F) * 20.0F, -MAX_Y_ROTATION, MAX_Y_ROTATION);
            float angleX = Mth.clamp((float)Math.atan(mousePosY / 40.0F) * 20.0F, -MAX_X_ROTATION, MAX_X_ROTATION);

            // поворачиваем модель на 180 градусов вокруг оси y, чтобы гаст смотрел вперед
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

            // применяем повороты для отслеживания мыши
            poseStack.mulPose(Axis.YP.rotationDegrees(angleY));
            poseStack.mulPose(Axis.XP.rotationDegrees(angleX));

            // исправление перевернутости гаста
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));

            // смещаем вертикально для центрирования
            poseStack.translate(0.0D, modelY, 0.0D);

            // настраиваем освещение
            Lighting.setupForEntityInInventory();

            // получаем рендеринг компоненты
            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            // устанавливаем идентичную ориентацию камеры (предотвращает влияние внешних факторов)
            dispatcher.overrideCameraOrientation(new Quaternionf());
            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

            // отключаем тени
            dispatcher.setRenderShadow(false);

            // рендерим сущность
            RenderSystem.runAsFancy(() -> {
                dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                        poseStack, bufferSource, 15728880);
            });

            // завершаем рендеринг
            bufferSource.endBatch();
            dispatcher.setRenderShadow(true);

            // восстанавливаем освещение
            Lighting.setupFor3DItems();

            // восстанавливаем оригинальные значения поворотов сущности
            entity.setYRot(originalYRot);
            entity.setXRot(originalXRot);
            entity.yHeadRot = originalYHeadRot;
            entity.yBodyRot = originalYBodyRot;

            // восстанавливаем состояние
            poseStack.popPose();
        } catch (Exception e) {
            LOGGER.error("Error rendering entity: {}", e.getMessage());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // заголовок инвентаря
        Component title = Component.literal("Happy Ghast Inventory");
        guiGraphics.drawString(this.font, title, 8, 6, 0x404040, false);

        // заголовок инвентаря игрока
        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}