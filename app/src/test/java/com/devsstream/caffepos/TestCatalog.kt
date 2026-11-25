package com.devsstream.caffepos

import com.devsstream.caffepos.core.model.*

fun buildTestCatalog(): Catalog {
    val milkGroup = ModifierGroup(
        id = "milk", name = "Milk", max = 1,
        options = listOf(
            ModifierOption("milk_oat", "Oat", 60),
            ModifierOption("milk_almond", "Almond", 70)
        )
    )
    val shotsGroup = ModifierGroup(
        id = "shots", name = "Shots", max = 2,
        options = listOf(ModifierOption("extra_shot", "Extra shot", 50))
    )
    val syrupGroup = ModifierGroup(
        id = "syrup", name = "Syrup", maxDistinct = 2, maxPerOption = 1,
        options = listOf(
            ModifierOption("vanilla", "Vanilla", 30),
            ModifierOption("caramel", "Caramel", 30),
            ModifierOption("hazelnut", "Hazelnut", 35)
        )
    )
    val rules = listOf(
        ModifierRule("R1", RuleType.REQUIRES, "hazelnut", listOf("milk_oat", "milk_almond")),
        ModifierRule("R2", RuleType.EXCLUDES, "milk_almond", listOf("caramel"))
    )
    val products = listOf(
        Product("americano", "Americano", ProductCategory.BEVERAGE, 180,
            listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 40)),
            listOf("milk", "shots", "syrup")),
        Product("latte", "Latte", ProductCategory.BEVERAGE, 220,
            listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 50)),
            listOf("milk", "shots", "syrup")),
        Product("iced_tea", "Iced Tea", ProductCategory.BEVERAGE, 120,
            listOf(ProductSize("reg", "Regular", 0), ProductSize("lrg", "Large", 30)),
            listOf("syrup")),
        Product("chicken_sandwich", "Chicken Sandwich", ProductCategory.FOOD, 320, emptyList(), emptyList()),
        Product("veg_wrap", "Veg Wrap", ProductCategory.FOOD, 260, emptyList(), emptyList()),
        Product("croissant", "Croissant", ProductCategory.BAKERY, 150, emptyList(), emptyList()),
        Product("brownie", "Brownie", ProductCategory.BAKERY, 120, emptyList(), emptyList())
    )
    return Catalog(
        version = 1,
        products = products,
        modifierGroups = listOf(milkGroup, shotsGroup, syrupGroup),
        rules = rules,
        promotions = listOf(
            Promotion("P1", PromotionType.BAKERY_3_FOR_2),
            Promotion("P2", PromotionType.HAPPY_HOUR_BEVERAGE)
        )
    )
}
