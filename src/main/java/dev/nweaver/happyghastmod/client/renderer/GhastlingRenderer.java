package dev.nweaver.happyghastmod.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack; // импорт для posestack
import dev.nweaver.happyghastmod.HappyGhastMod;
import dev.nweaver.happyghastmod.client.model.GhastlingModel; // импорт модели гастлинга
import dev.nweaver.happyghastmod.entity.Ghastling; // импорт сущности гастлинга
import dev.nweaver.happyghastmod.init.ModelLayersInit; // импорт инициализатора слоев моделей
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer; // наследуемся от mobrenderer
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

// рендер для гастлинга
// использует ghastlingmodel для геометрии (с короткими тентаклями),
// применяет текстуру гастлинга и масштабирует модель
@OnlyIn(Dist.CLIENT)
public class GhastlingRenderer extends MobRenderer<Ghastling, GhastlingModel<Ghastling>> {

    // путь к текстуре гастлинга в ресурсах мода
    private static final ResourceLocation GHASTLING_TEXTURE =
            HappyGhastMod.rl("textures/entity/ghastling.png");

    // коэффициент масштабирования для размера модели
    private static final float SCALE_FACTOR = 1.6F; // используй значение, которое ты подобрал

    // конструктор рендерера
    // @param context контекст рендерера, предоставляемый forge/minecraft
    public GhastlingRenderer(EntityRendererProvider.Context context) {
        // вызываем конструктор родительского класса mobrenderer:
        // передаем контекст
        // создаем новый экземпляр нашей ghastlingmodel, загружая геометрию
        //    из соответствующего слоя (modellayersinitghastling)
        //    contextgetmodelset()bakelayer() получает готовую к рендерингу модель из зарегистрированного определения слоя
        // устанавливаем радиус тени
        //    базовая тень гаста 0.5f, умножаем ее на масштаб
        super(context, new GhastlingModel<>(context.getModelSet().bakeLayer(ModelLayersInit.GHASTLING)), 0.5F * SCALE_FACTOR);
    }

    // возвращает resourcelocation текстуры для данного гастлинга
    // вызывается каждый кадр для определения, какую текстуру использовать
    @Override
    public ResourceLocation getTextureLocation(Ghastling ghastling) {
        // возвращаем всегда одну и ту же текстуру для гастлинга
        return GHASTLING_TEXTURE;
    }

    // применяет масштабирование к модели перед ее рендерингом
    @Override
    protected void scale(Ghastling ghastling, PoseStack poseStack, float partialTickTime) {
        super.scale(ghastling, poseStack, partialTickTime);

        // применяем наш коэффициент масштабирования ко всем трем осям
        poseStack.scale(SCALE_FACTOR, SCALE_FACTOR, SCALE_FACTOR);

    }
}