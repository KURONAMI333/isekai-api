package com.kuronami.isekaiapi.impl.wood.datagen;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * The {@code en_us} or {@code ja_jp} names of every wood entry in one namespace.
 *
 * <p>{@code LanguageProvider} writes the whole {@code lang/<locale>.json} of a mod, so this
 * provider exists only while the consumer asked for it: {@code WoodSetDataGen.lang(false)} turns
 * the names off for a consumer that already has its own language provider, and
 * {@code WoodSetDataGen.translations} hands out the same map to merge by hand.
 */
@ApiStatus.Internal
public final class WoodLanguageProvider extends LanguageProvider {

    private final String namespace;
    private final String locale;
    private final List<WoodEntry> entries;
    private final BooleanSupplier enabled;

    public WoodLanguageProvider(PackOutput output, String namespace, String locale,
                                List<WoodEntry> entries, BooleanSupplier enabled) {
        super(output, namespace, locale);
        this.namespace = namespace;
        this.locale = locale;
        this.entries = entries;
        this.enabled = enabled;
    }

    @Override
    protected void addTranslations() {
        if (!enabled.getAsBoolean()) {
            return;
        }
        for (WoodEntry entry : entries) {
            if (!entry.set().id().getNamespace().equals(namespace)) {
                continue;
            }
            WoodLanguages.names(entry, locale).forEach(this::add);
        }
    }
}
