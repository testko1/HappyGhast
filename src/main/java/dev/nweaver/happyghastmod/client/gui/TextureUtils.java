package dev.nweaver.happyghastmod.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.nweaver.happyghastmod.util.FolderTextureLoader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

// утилиты для работы с текстурами
public class TextureUtils {

    // возвращает список доступных файлов текстур
    public static List<File> getAvailableTextures() {
        return FolderTextureLoader.getAvailableTextures();
    }

    // возвращает папку с текстурами
    public static File getTexturesFolder() {
        return FolderTextureLoader.getTexturesFolder();
    }

    public static boolean validateTextureAspectRatio(byte[] textureData, String textureType) {
        try {
            NativeImage nativeImage = NativeImage.read(new ByteArrayInputStream(textureData));
            int width = nativeImage.getWidth();
            int height = nativeImage.getHeight();
            double aspectRatio = (double)width / height;

            boolean isValid = switch (textureType) {
                case "saddle" -> Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для седла
                case "glasses" -> Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для очков
                case "accessory" -> Math.abs(aspectRatio - 2.0) < 0.1; // 2:1 для аксессуаров
                default -> false;
            };

            nativeImage.close();
            return isValid;
        } catch (Exception e) {
            return false;
        }
    }
}