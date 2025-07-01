package dev.nweaver.happyghastmod.client.gui;

import dev.nweaver.happyghastmod.entity.HappyGhast;
import dev.nweaver.happyghastmod.init.EntityInit;
import dev.nweaver.happyghastmod.network.NetworkHandler;
import dev.nweaver.happyghastmod.network.RegisterCustomHarnessPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// экран создания кастомных сбруй для счастливых гастов
// адаптивная и компактная версия
public class CustomHarnessCreator extends Screen {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String PREVIEW_HARNESS_ID = "preview-harness-id";

    // вспомогательные классы
    private final UILayoutHelper layout;
    private final HarnessTextureManager textureManager;
    private final EntityPreviewRenderer entityRenderer;
    private final StatusMessageManager statusManager;

    // основные компоненты ui
    private EditBox harnessNameField;
    private List<TexturePreviewButton> textureButtons = new ArrayList<>();

    // текущий выбранный тип текстуры
    // устанавливаем "saddle" как тип по умолчанию
    private String currentTextureType = "saddle"; // "saddle", "glasses", "accessory"

    // прокрутка для списка текстур
    private int scrollOffset = 0;
    private int maxTexturesVisible = 3;

    public CustomHarnessCreator() {
        super(Component.literal("Create Custom Harness"));

        // инициализация вспомогательных классов
        this.layout = new UILayoutHelper();
        this.textureManager = new HarnessTextureManager(PREVIEW_HARNESS_ID);
        this.statusManager = new StatusMessageManager();

        // создаем превью гаста
        HappyGhast previewGhast = null;
        try {
            if (Minecraft.getInstance().level != null) {
                previewGhast = (HappyGhast) EntityInit.HAPPY_GHAST.get().create(Minecraft.getInstance().level);
                if (previewGhast != null) {
                    previewGhast.setSaddled(true);
                    previewGhast.setHarnessColor("blue"); // начальный цвет
                }
            }
        } catch (Exception e) {
            // ошибка создания превью
        }
        this.entityRenderer = new EntityPreviewRenderer(previewGhast);

        // сбрасываем предыдущее превью сбруи
        try {
            dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager.unregisterPreviewHarness(PREVIEW_HARNESS_ID);
        } catch (Exception e) {
            // игнорируем ошибки, так как может быть первый запуск
        }
    }

    @Override
    protected void init() {
        // настройка размеров и позиций ui элементов
        layout.calculateLayout(width, height);

        // создание поля имени сбруи
        harnessNameField = new EditBox(this.font, layout.getNameFieldX(), layout.getNameFieldY(),
                layout.getNameFieldWidth(), 20, Component.empty());
        harnessNameField.setMaxLength(32);
        harnessNameField.setValue("");
        harnessNameField.setHint(Component.literal("Enter harness name"));
        this.addRenderableWidget(harnessNameField);

        // кнопка открытия папки с текстурами
        this.addRenderableWidget(Button.builder(Component.literal("Open Folder"), button -> openTexturesFolder())
                .pos(layout.getFolderButtonX(), layout.getFolderButtonY())
                .size(90, 20)
                .build());

        // кнопки выбора типа текстуры
        addTextureTypeButtons();

        // кнопки навигации
        addNavigationButtons();

        // кнопки создания/отмены
        addActionButtons();

        // обновляем список текстур
        refreshTextureList();
        this.textureManager.setPreviewUpdateCallback(new HarnessTextureManager.PreviewUpdateCallback() {
            @Override
            public void onTextureSelected() {
                // запускаем отложенное обновление превью
                Minecraft.getInstance().execute(() -> {
                    String previewName = harnessNameField.getValue().isEmpty() ?
                            "Предварительный просмотр" : harnessNameField.getValue();
                    updatePreviewHarness();
                });
            }
        });
    }

    private void addTextureTypeButtons() {
        int tabWidth = 60;
        int tabHeight = 20;

        this.addRenderableWidget(Button.builder(Component.literal("Saddle"), button -> selectTextureType("saddle"))
                .pos(layout.getTypeButtonX(), layout.getTypeButtonY())
                .size(tabWidth, tabHeight)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Glasses"), button -> selectTextureType("glasses"))
                .pos(layout.getTypeButtonX() + tabWidth + 5, layout.getTypeButtonY()) // помещаем справа от saddle
                .size(tabWidth, tabHeight)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Accessory"), button -> selectTextureType("accessory"))
                .pos(layout.getTypeButtonX(), layout.getTypeButtonY() + tabHeight + 5) // помещаем под saddle
                .size(tabWidth, tabHeight)
                .build());
    }

    private void addNavigationButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("◀"), button -> scrollUp())
                .pos(layout.getLeftNavButtonX(), layout.getNavButtonY())
                .size(30, 30)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("▶"), button -> scrollDown())
                .pos(layout.getRightNavButtonX(), layout.getNavButtonY())
                .size(30, 30)
                .build());
    }

    private void addActionButtons() {
        this.addRenderableWidget(Button.builder(Component.literal("CREATE HARNESS"), button -> createHarness())
                .pos(layout.getCreateButtonX(), layout.getCreateButtonY())
                .size(130, 30)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .pos(layout.getCancelButtonX(), layout.getCancelButtonY())
                .size(70, 25)
                .build());
    }

    @Override
    public void onClose() {
        try {
            dev.nweaver.happyghastmod.client.texture.ClientCustomHarnessManager.unregisterPreviewHarness(PREVIEW_HARNESS_ID);
        } catch (Exception e) {
        }
        super.onClose();
    }

    private void refreshTextureList() {
        // удаляем старые кнопки текстур
        for (TexturePreviewButton button : textureButtons) {
            this.removeWidget(button);
        }
        textureButtons.clear();

        // получаем доступные текстуры
        List<File> textures = TextureUtils.getAvailableTextures();
        if (textures.isEmpty()) {
            return;
        }

        // ограничиваем прокрутку
        maxTexturesVisible = 1;
        int maxScrollOffset = Math.max(0, textures.size() - maxTexturesVisible);
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);

        // добавляем кнопку для текущей текстуры
        if (scrollOffset < textures.size()) {
            File file = textures.get(scrollOffset);
            TexturePreviewButton button = new TexturePreviewButton(
                    file,
                    layout.getTextureButtonX(),
                    layout.getTextureButtonY(),
                    layout.getTextureButtonWidth(),
                    layout.getTextureButtonHeight(),
                    b -> selectTexture(((TexturePreviewButton) b).getTextureFile())
            );

            // устанавливаем текущий выбранный тип текстуры для правильного отображения
            button.setSelectedTextureType(currentTextureType);

            textureButtons.add(button);
            this.addRenderableWidget(button);
        }
    }

    private void selectTextureType(String textureType) {
        this.currentTextureType = textureType;

        // обновляем тип текстуры в существующих кнопках
        for (TexturePreviewButton button : textureButtons) {
            button.setSelectedTextureType(textureType);
        }

        // можно также обновить отображение для подсказки пользователю
        statusManager.showStatus(Component.literal("Selected texture type: " + textureType), 0x55FFFF);
    }

    private void selectTexture(File textureFile) {
        // передаем текстуру для обработки в менеджер текстур
        textureManager.selectTexture(textureFile, currentTextureType);

        // обновляем превью
        updatePreviewHarness();
    }

    private void updatePreviewHarness() {
        String previewName = harnessNameField.getValue().isEmpty() ?
                "Preview" : harnessNameField.getValue();

        textureManager.updatePreviewHarness(previewName, entityRenderer.getPreviewEntity());
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
            refreshTextureList();
        }
    }

    private void scrollDown() {
        List<File> textures = TextureUtils.getAvailableTextures();
        int maxScrollOffset = Math.max(0, textures.size() - maxTexturesVisible);
        if (scrollOffset < maxScrollOffset) {
            scrollOffset++;
            refreshTextureList();
        }
    }

    private void openTexturesFolder() {
        File folder = TextureUtils.getTexturesFolder();
        try {
            java.awt.Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            statusManager.showStatus(Component.literal("Folder: " + folder.getAbsolutePath()), 0xFFFFAA);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // рендерим фон экрана
        renderBackground(graphics);

        // рендерим основную панель
        layout.renderMainPanel(graphics, font, title);

        // рендерим надписи
        graphics.drawString(this.font, Component.literal("Name:"), layout.getNameLabelX(), layout.getNameLabelY(), 0xFFFFFF);

        // рендерим превью гаста
        entityRenderer.renderPreview(graphics, layout.getPreviewX(), layout.getPreviewY(),
                layout.getPreviewWidth(), layout.getPreviewHeight(), font);

        // рендерим область выбора текстур
        renderTextureSelectionArea(graphics);

        // рендерим выбранные текстуры
        textureManager.renderSelectedTexturesBlock(graphics, layout, font);

        // рендерим статус-сообщение
        statusManager.renderStatusMessage(graphics, font, layout.getStatusX(), layout.getStatusY());

        // рендерим остальные элементы ui
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTextureSelectionArea(GuiGraphics graphics) {
        int textureAreaY = layout.getTextureAreaY();

        // выводим заголовок в зависимости от типа текстуры
        String currentTypeLabel = switch (currentTextureType) {
            case "saddle" -> "Selecting Saddle Texture";
            case "glasses" -> "Selecting Glasses Texture";
            case "accessory" -> "Selecting Accessory Texture";
            default -> "Select Texture";
        };

        graphics.drawCenteredString(this.font, Component.literal(currentTypeLabel),
                layout.getLeftX() + layout.getGuiWidth() / 2, textureAreaY + 5, 0xFFFF55);

        // выводим счетчик текстур
        List<File> textures = TextureUtils.getAvailableTextures();
        if (!textures.isEmpty()) {
            String counter = (scrollOffset + 1) + "/" + textures.size();
            graphics.drawString(this.font, Component.literal(counter),
                    layout.getLeftX() + layout.getGuiWidth() / 2 - 15, textureAreaY + 20, 0xCCCCCC);
        }

        // выводим разделитель
        graphics.fill(layout.getLeftX() + 15, textureAreaY + layout.getTextureAreaHeight(),
                layout.getLeftX() + layout.getGuiWidth() - 15, textureAreaY + layout.getTextureAreaHeight() + 2, 0xFF555555);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // проверяем клик по области превью
        if (entityRenderer.isInPreviewArea(mouseX, mouseY, layout.getPreviewX(), layout.getPreviewY(),
                layout.getPreviewWidth(), layout.getPreviewHeight())) {
            entityRenderer.startRotating((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        entityRenderer.stopRotating();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (entityRenderer.isRotating()) {
            entityRenderer.updateRotation((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void createHarness() {
        String harnessName = harnessNameField.getValue().trim();

        // проверяем наличие имени сбруи
        if (harnessName.isEmpty()) {
            statusManager.showStatus(Component.literal("Please enter a harness name"), 0xFF5555);
            return;
        }

        // проверяем корректность соотношения сторон для всех текстур
        if (!validateAllTexturesAspectRatio()) {
            return;
        }

        // генерируем уникальный id для сбруи
        String harnessId = UUID.randomUUID().toString();

        try {
            // отправляем данные на сервер
            NetworkHandler.sendToServer(new RegisterCustomHarnessPacket(
                    harnessId,
                    harnessName,
                    textureManager.getSaddleTextureData(),
                    textureManager.getGlassesTextureData(),
                    textureManager.getAccessoryTextureData()
            ));

            // показываем сообщение об успехе
            statusManager.showStatus(Component.literal("Harness created successfully!"), 0x55FF55);

            // закрываем экран через 2 секунды
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(null));
                } catch (InterruptedException e) {
                    // игнорируем
                }
            }).start();
        } catch (Exception e) {
            statusManager.showStatus(Component.literal("Error creating harness"), 0xFF5555);
        }
    }

    private boolean validateAllTexturesAspectRatio() {
        // проверяем седло, если оно выбрано
        if (textureManager.getSaddleTextureData() != null &&
                !TextureUtils.validateTextureAspectRatio(textureManager.getSaddleTextureData(), "saddle")) {
            statusManager.showStatus(Component.literal("Saddle texture must have 2:1 aspect ratio"), 0xFF5555);
            return false;
        }

        // проверяем очки, если они выбраны
        if (textureManager.getGlassesTextureData() != null &&
                !TextureUtils.validateTextureAspectRatio(textureManager.getGlassesTextureData(), "glasses")) {
            statusManager.showStatus(Component.literal("Glasses texture must have 2:1 aspect ratio"), 0xFF5555);
            return false;
        }

        // проверяем аксессуар, если он выбран
        if (textureManager.getAccessoryTextureData() != null &&
                !TextureUtils.validateTextureAspectRatio(textureManager.getAccessoryTextureData(), "accessory")) {
            statusManager.showStatus(Component.literal("Accessory texture must have 2:1 aspect ratio"), 0xFF5555); // изменено на 2:1, как в textureutils
            return false;
        }

        return true;
    }
}