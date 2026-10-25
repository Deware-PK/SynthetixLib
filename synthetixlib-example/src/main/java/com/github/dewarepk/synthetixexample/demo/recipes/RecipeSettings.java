package com.github.dewarepk.synthetixexample.demo.recipes;

import com.github.dewarepk.synthetixlib.config.annotation.Comment;
import com.github.dewarepk.synthetixlib.config.annotation.Range;

/** The settings of the recipes, {@code recipes.yml}. */
@Comment("Recipe settings")
public record RecipeSettings(@Comment("Items of the daily reward") @Range(min = 1, max = 64) int dailyAmount) {

    public static RecipeSettings defaults() {
        return new RecipeSettings(1);
    }
}
