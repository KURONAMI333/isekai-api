package com.kuronami.isekaiapi.impl.wood.validation;

import com.kuronami.isekaiapi.IsekaiApi;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.ApiStatus;

/**
 * A {@link WoodSetProbe} over the packs the running game has selected.
 *
 * <p>A {@link ResourceManager} only answers for the {@link PackType} it was built with, but it also
 * exposes the selected {@link PackResources}, and those can be asked for either type. That is how
 * the server-side {@code /isekai validate} reaches {@code assets/}: mod jars are one pack holding
 * both {@code data/} and {@code assets/}, so the same jar that provides the blocks also provides
 * their models and textures.
 *
 * <p>Packs are consulted in the order the game resolves them (highest priority last), so an
 * override in a resource pack is the file that is checked.
 */
@ApiStatus.Internal
public final class PackProbe implements WoodSetProbe {

    /** Lowest priority first, as {@code MultiPackResourceManager} keeps them. */
    private final List<PackResources> packs;

    private final Map<String, Boolean> namespaceCache = new HashMap<>();

    public PackProbe(ResourceManager resources) {
        this.packs = resources.listPacks().toList();
    }

    @Override
    public boolean seesData(String namespace) {
        return sees(PackType.SERVER_DATA, namespace);
    }

    @Override
    public boolean seesAssets(String namespace) {
        return sees(PackType.CLIENT_RESOURCES, namespace);
    }

    private boolean sees(PackType type, String namespace) {
        return namespaceCache.computeIfAbsent(type.getDirectory() + "/" + namespace,
                key -> packs.stream().anyMatch(pack -> pack.getNamespaces(type).contains(namespace)));
    }

    @Override
    public Optional<String> readData(ResourceLocation id) {
        return read(PackType.SERVER_DATA, id);
    }

    @Override
    public Optional<String> readAsset(ResourceLocation id) {
        return read(PackType.CLIENT_RESOURCES, id);
    }

    /** The text of {@code id}, from the highest-priority pack that has it. */
    private Optional<String> read(PackType type, ResourceLocation id) {
        for (int i = packs.size() - 1; i >= 0; i--) {
            PackResources pack = packs.get(i);
            IoSupplier<InputStream> supplier = pack.getResource(type, id);
            if (supplier == null) {
                continue;
            }
            Optional<String> text = read(pack, id, supplier);
            if (text.isPresent()) {
                return text;
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<ResourceLocation, String> listData(String namespace, String directory) {
        // One entry per file: the first pack to offer it is the one the game would load.
        Map<ResourceLocation, IoSupplier<InputStream>> files = new LinkedHashMap<>();
        for (int i = packs.size() - 1; i >= 0; i--) {
            packs.get(i).listResources(PackType.SERVER_DATA, namespace, directory, files::putIfAbsent);
        }
        Map<ResourceLocation, String> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, IoSupplier<InputStream>> entry : files.entrySet()) {
            read(null, entry.getKey(), entry.getValue()).ifPresent(text -> result.put(entry.getKey(), text));
        }
        return result;
    }

    private static Optional<String> read(PackResources pack, ResourceLocation id,
                                         IoSupplier<InputStream> supplier) {
        try (InputStream stream = supplier.get()) {
            return Optional.of(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            IsekaiApi.LOGGER.warn("[Isekai] validate could not read {} (pack {})",
                    id, pack == null ? "?" : pack.packId(), e);
            return Optional.empty();
        }
    }

    @Override
    public boolean inBlockTag(Block block, TagKey<Block> tag) {
        return block.defaultBlockState().is(tag);
    }

    @Override
    public boolean inItemTag(Item item, TagKey<Item> tag) {
        return item.builtInRegistryHolder().is(tag);
    }
}
