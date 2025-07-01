package dev.nweaver.happyghastmod.client.model; // убедись что пакет правильный

import net.minecraft.client.model.GhastModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity; // изменили тип на entity, тк ghastling не ездовой
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
// наследуемся от ghastmodel
public class GhastlingModel<T extends Entity> extends GhastModel<T> {

    public GhastlingModel(ModelPart modelPart) {
        super(modelPart);
    }

    // оставляем этот вспомогательный метод
    private static String createTentacleName(int index) {
        return "tentacle" + index;
    }

    // этот метод создает геометрию с короткими тентаклями, копируем его из happyghastmodel
    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();

        partDefinition.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 17.6F, 0.0F));

        RandomSource randomSource = RandomSource.create(1660L);

        // используем те же короткие длины, что и для счастливого гаста
        int[] tentacleLengths = { 4, 5, 4, 5, 6, 5, 4, 5, 4 };

        for(int i = 0; i < 9; ++i) {
            float f = (((float)(i % 3) - (float)(i / 3 % 2) * 0.5F + 0.25F) / 2.0F * 2.0F - 1.0F) * 5.0F;
            float f1 = ((float)(i / 3) / 2.0F * 2.0F - 1.0F) * 5.0F;
            randomSource.nextInt(7); // пропускаем для совместимости позиций
            int length = tentacleLengths[i];

            partDefinition.addOrReplaceChild(
                    createTentacleName(i),
                    CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, (float)length, 2.0F),
                    PartPose.offset(f, 24.6F, f1) // та же высота смещения, что и у стандартного гаста
            );
        }

        // текстура модели та же, что у гаста (64x32)
        return LayerDefinition.create(meshDefinition, 64, 32);
    }

}