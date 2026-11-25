package com.devsstream.caffepos

import com.devsstream.caffepos.core.catalog.validate
import com.devsstream.caffepos.core.model.CartItem
import com.devsstream.caffepos.core.model.SelectedModifier
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogValidatorTest {

    private val catalog = buildTestCatalog()

    @Test
    fun `TC-01 latte with no size is blocked`() {
        val item = CartItem(productId = "latte", sizeId = null, modifiers = emptyList())
        val violations = validate(item, catalog)
        assertTrue("Expected size_required violation", violations.any { it.ruleId == "size_required" })
    }

    @Test
    fun `TC-02 americano regular with 3 extra shots is blocked`() {
        val item = CartItem(
            productId = "americano",
            sizeId = "reg",
            modifiers = listOf(SelectedModifier("extra_shot", "shots", quantity = 3))
        )
        val violations = validate(item, catalog)
        assertTrue("Expected shots max violation", violations.any { it.ruleId == "shots" })
    }

    @Test
    fun `TC-03 latte regular with hazelnut and no milk is blocked`() {
        val item = CartItem(
            productId = "latte",
            sizeId = "reg",
            modifiers = listOf(SelectedModifier("hazelnut", "syrup"))
        )
        val violations = validate(item, catalog)
        assertTrue("Expected R1 violation", violations.any { it.ruleId == "R1" })
    }

    @Test
    fun `TC-04 latte large with almond caramel hazelnut vanilla returns both violations`() {
        val item = CartItem(
            productId = "latte",
            sizeId = "lrg",
            modifiers = listOf(
                SelectedModifier("milk_almond", "milk"),
                SelectedModifier("caramel", "syrup"),
                SelectedModifier("hazelnut", "syrup"),
                SelectedModifier("vanilla", "syrup")
            )
        )
        val violations = validate(item, catalog)
        assertTrue("Expected syrup maxDistinct violation", violations.any { it.ruleId == "syrup" })
        assertTrue("Expected R2 violation", violations.any { it.ruleId == "R2" })
    }

    @Test
    fun `valid latte large with oat and vanilla passes`() {
        val item = CartItem(
            productId = "latte",
            sizeId = "lrg",
            modifiers = listOf(
                SelectedModifier("milk_oat", "milk"),
                SelectedModifier("vanilla", "syrup")
            )
        )
        val violations = validate(item, catalog)
        assertTrue(violations.isEmpty())
    }
}
