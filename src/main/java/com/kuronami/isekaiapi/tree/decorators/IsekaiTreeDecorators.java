package com.kuronami.isekaiapi.tree.decorators;

import com.kuronami.isekaiapi.IsekaiApi;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;

/**
 * Registers Isekai's tree decorators into the vanilla {@code TREE_DECORATOR_TYPE} registry, so a
 * datapack references them from a {@code minecraft:tree} configured feature's {@code decorators}
 * array as {@code "type": "isekai_api:<name>"}.
 *
 * <p>Decorators are the second half of tree shape: the placers in
 * {@code com.kuronami.isekaiapi.tree} decide where logs and leaves go, and these add what grows on
 * them. Every field is a {@code BlockStateProvider} slot, so the primitives carry no baked species,
 * biome or theme.
 *
 * <p>Currently registered:
 * <ul>
 *   <li>{@code isekai_api:attach_to_logs} — blocks on the sides of the trunk, see
 *       {@link AttachToLogsDecorator}.</li>
 *   <li>{@code isekai_api:ground_patch} — a patch of blocks on the ground around the base, see
 *       {@link GroundPatchDecorator}.</li>
 *   <li>{@code isekai_api:hang_from_leaves} — strands hanging from the underside of leaves, see
 *       {@link HangFromLeavesDecorator}.</li>
 *   <li>{@code isekai_api:snow_cap} — snow layers on the sky-facing leaves, see
 *       {@link SnowCapDecorator}.</li>
 *   <li>{@code isekai_api:branch_stubs} — short bare branches out of the trunk's side, see
 *       {@link BranchStubsDecorator}.</li>
 *   <li>{@code isekai_api:broken_top} — snaps the highest logs off, see
 *       {@link BrokenTopDecorator}.</li>
 * </ul>
 */
@ApiStatus.Internal
public final class IsekaiTreeDecorators {

    public static final DeferredRegister<TreeDecoratorType<?>> DECORATORS =
            DeferredRegister.create(BuiltInRegistries.TREE_DECORATOR_TYPE, IsekaiApi.MODID);

    public static final Supplier<TreeDecoratorType<AttachToLogsDecorator>> ATTACH_TO_LOGS =
            DECORATORS.register("attach_to_logs", () -> new TreeDecoratorType<>(AttachToLogsDecorator.CODEC));
    public static final Supplier<TreeDecoratorType<GroundPatchDecorator>> GROUND_PATCH =
            DECORATORS.register("ground_patch", () -> new TreeDecoratorType<>(GroundPatchDecorator.CODEC));
    public static final Supplier<TreeDecoratorType<HangFromLeavesDecorator>> HANG_FROM_LEAVES =
            DECORATORS.register("hang_from_leaves", () -> new TreeDecoratorType<>(HangFromLeavesDecorator.CODEC));
    public static final Supplier<TreeDecoratorType<SnowCapDecorator>> SNOW_CAP =
            DECORATORS.register("snow_cap", () -> new TreeDecoratorType<>(SnowCapDecorator.CODEC));
    public static final Supplier<TreeDecoratorType<BranchStubsDecorator>> BRANCH_STUBS =
            DECORATORS.register("branch_stubs", () -> new TreeDecoratorType<>(BranchStubsDecorator.CODEC));
    public static final Supplier<TreeDecoratorType<BrokenTopDecorator>> BROKEN_TOP =
            DECORATORS.register("broken_top", () -> new TreeDecoratorType<>(BrokenTopDecorator.CODEC));

    private IsekaiTreeDecorators() {}

    public static void register(IEventBus modBus) {
        DECORATORS.register(modBus);
        IsekaiApi.LOGGER.info("[Isekai] tree decorators registered: attach_to_logs, ground_patch, "
                + "hang_from_leaves, snow_cap, branch_stubs, broken_top");
    }
}
