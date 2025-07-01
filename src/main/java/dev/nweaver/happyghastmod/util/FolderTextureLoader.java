package dev.nweaver.happyghastmod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

// загружает текстуры из определенной директории игры
public class FolderTextureLoader {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final String TEXTURES_DIR = "happyghast_textures";

    // получает папку для текстур, создает её если она не существует
    public static File getTexturesFolder() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        File texturesDir = new File(gameDir, TEXTURES_DIR);

        if (!texturesDir.exists()) {
            if (texturesDir.mkdirs()) {
                LOGGER.info("Создана директория для текстур: {}", texturesDir.getAbsolutePath());
            } else {
                LOGGER.error("Не удалось создать директорию для текстур: {}", texturesDir.getAbsolutePath());
            }
        }

        return texturesDir;
    }

    // получает список всех png файлов в папке текстур
    public static List<File> getAvailableTextures() {
        List<File> textures = new ArrayList<>();
        File texturesDir = getTexturesFolder();

        if (texturesDir.exists() && texturesDir.isDirectory()) {
            File[] files = texturesDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".png"));

            if (files != null) {
                for (File file : files) {
                    textures.add(file);
                }
            }
        }

        return textures;
    }


}