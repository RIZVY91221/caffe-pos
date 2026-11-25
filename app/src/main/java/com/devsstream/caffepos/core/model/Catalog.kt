package com.devsstream.caffepos.core.model

/** All prices are in BDT (1 BDT = 100 poisha in the engine). Stored here as BDT for readability. */
data class Catalog(
    val version: Int,
    val products: List<Product>,
    val modifierGroups: List<ModifierGroup>,
    val rules: List<ModifierRule>,
    val promotions: List<Promotion>
)

data class Product(
    val id: String,
    val name: String,
    val category: ProductCategory,
    val basePriceBdt: Int,
    val sizes: List<ProductSize>,          // empty means no size selector
    val modifierGroupIds: List<String>
)

data class ProductSize(
    val id: String,
    val label: String,
    val extraPriceBdt: Int
)

data class ModifierGroup(
    val id: String,
    val name: String,
    val max: Int? = null,
    val maxDistinct: Int? = null,
    val maxPerOption: Int? = null,
    val options: List<ModifierOption>
)

data class ModifierOption(
    val id: String,
    val name: String,
    val priceBdt: Int
)

data class ModifierRule(
    val id: String,
    val type: RuleType,
    val optionId: String,
    val targetOptionIds: List<String>
)

enum class RuleType { REQUIRES, EXCLUDES }

data class Promotion(
    val id: String,
    val type: PromotionType
)

enum class PromotionType {
    BAKERY_3_FOR_2,
    HAPPY_HOUR_BEVERAGE
}

enum class ProductCategory { BEVERAGE, FOOD, BAKERY }
