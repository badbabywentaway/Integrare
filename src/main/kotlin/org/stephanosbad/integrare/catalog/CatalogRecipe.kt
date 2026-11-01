/*
 * Integrare - Custom item provider abstraction for Paper plugins
 * Copyright (C) 2025 StephanosBad
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see <https://www.gnu.org/licenses/>.
 */
package org.stephanosbad.integrare.catalog

import org.bukkit.Material

/**
 * A crafting table recipe producing a catalog item.
 *
 * Recipes with a vanilla ingredient that doesn't exist on the running server
 * version are skipped, so newer materials can be used without breaking older servers.
 */
sealed interface CatalogRecipe {
    /** Recipe id without namespace; providers derive their recipe keys from it */
    val id: String
    /** Catalog id of the crafted item */
    val result: String
    val amount: Int
    val ingredients: List<Ingredient>

    fun isAvailable(resolveMaterial: (String) -> Material?): Boolean =
        ingredients.filterIsInstance<Ingredient.Vanilla>().all { resolveMaterial(it.material) != null }

    /**
     * @property shape Up to three rows of up to three characters; a space is an empty slot
     * @property keys Ingredient for each non-space character of [shape], in listing order
     */
    data class Shaped(
        override val id: String,
        override val result: String,
        val shape: List<String>,
        val keys: Map<Char, Ingredient>,
        override val amount: Int = 1
    ) : CatalogRecipe {
        init {
            val used = shape.joinToString("").toSet() - ' '
            require(used == keys.keys) { "Recipe $id: shape uses $used but keys are ${keys.keys}" }
        }

        override val ingredients: List<Ingredient> get() = keys.values.toList()

        /** How many slots of the shape [key] fills */
        fun count(key: Char): Int = shape.sumOf { row -> row.count { it == key } }
    }

    data class Shapeless(
        override val id: String,
        override val result: String,
        override val ingredients: List<Ingredient>,
        override val amount: Int = 1
    ) : CatalogRecipe
}

sealed interface Ingredient {
    /** A vanilla material, by name, e.g. "IRON_INGOT" */
    data class Vanilla(val material: String) : Ingredient

    /** Another item of the same catalog, by id */
    data class Custom(val id: String) : Ingredient
}
