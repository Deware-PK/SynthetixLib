package com.github.dewarepk.synthetixexample.demo.recipes;

import com.github.dewarepk.synthetixlib.command.CommandSpec;
import com.github.dewarepk.synthetixlib.config.LocaleHandle;
import java.time.Duration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Recipe 2: a command with a per-player cooldown whose every message comes from the language file. */
public final class CooldownRecipe {

    private CooldownRecipe() {
    }

    // @snippet:recipe-cooldown
    public static CommandSpec daily(final LocaleHandle<RecipeMessages> lang, final Material reward) {
        return CommandSpec.literal("daily")
                .aliases("dailyreward")
                .description("Claim the daily reward")
                .playerOnly()
                .cooldown(Duration.ofHours(24), "recipes.bypass")
                .executes(ctx -> {
                    if (ctx.player().getInventory().firstEmpty() == -1) {
                        throw ctx.fail(lang, RecipeMessages::inventoryFull);     // a failure does not start the cooldown
                    }
                    ctx.player().getInventory().addItem(new ItemStack(reward));
                    ctx.reply(lang, RecipeMessages::daily, Placeholder.unparsed("item", reward.key().value()));
                })
                .build();
    }
    // @end
}
