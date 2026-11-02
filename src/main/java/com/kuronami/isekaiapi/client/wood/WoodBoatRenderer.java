package com.kuronami.isekaiapi.client.wood;

import com.kuronami.isekaiapi.impl.wood.WoodSets;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.model.ListModel;
import net.minecraft.client.renderer.entity.BoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.ApiStatus;

/**
 * Boat and chest-boat renderer for a wood set declared through the public builder.
 *
 * <p>Vanilla's renderer already builds the boat models; it only maps the entity's
 * {@code Boat.Type} to a texture. A wood-set boat keeps the oak type, so this renderer reuses
 * vanilla's model and map and swaps the texture for the set's own:
 * {@code textures/entity/boat/<path>.png} and {@code textures/entity/chest_boat/<path>.png} in the
 * set's namespace.
 */
@OnlyIn(Dist.CLIENT)
@ApiStatus.Internal
public final class WoodBoatRenderer extends BoatRenderer {

    private final ResourceLocation texture;

    public WoodBoatRenderer(EntityRendererProvider.Context context, ResourceLocation woodSet, boolean chestBoat) {
        super(context, chestBoat);
        this.texture = WoodSets.boatTexture(woodSet, chestBoat);
    }

    @Override
    public Pair<ResourceLocation, ListModel<Boat>> getModelWithLocation(Boat boat) {
        return Pair.of(texture, super.getModelWithLocation(boat).getSecond());
    }
}
