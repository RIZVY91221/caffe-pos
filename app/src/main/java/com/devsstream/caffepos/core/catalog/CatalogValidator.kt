package com.devsstream.caffepos.core.catalog

import com.devsstream.caffepos.core.model.CartItem
import com.devsstream.caffepos.core.model.Catalog
import com.devsstream.caffepos.core.model.RuleType
import com.devsstream.caffepos.core.model.Violation

/**
 * Pure function: validates a single CartItem against the full catalog.
 * Returns ALL violations, not just the first.
 * The Add-to-Order button must stay disabled while any violation exists.
 */
fun validate(item: CartItem, catalog: Catalog): List<Violation> {
    val violations = mutableListOf<Violation>()
    val product = catalog.products.find { it.id == item.productId }
        ?: return listOf(Violation("unknown_product", "Product not found in catalog"))

    // --- Size check ---
    if (product.sizes.isNotEmpty()) {
        if (item.sizeId == null || product.sizes.none { it.id == item.sizeId }) {
            violations += Violation("size_required", "A size is required for ${product.name}")
        }
    }

    // --- Modifier group checks ---
    for (groupId in product.modifierGroupIds) {
        val group = catalog.modifierGroups.find { it.id == groupId } ?: continue
        val selectedInGroup = item.modifiers.filter { it.groupId == groupId }
        val totalUnits = selectedInGroup.sumOf { it.quantity }
        val distinctCount = selectedInGroup.distinctBy { it.optionId }.size

        // max total units
        if (group.max != null && totalUnits > group.max) {
            violations += Violation(
                groupId,
                "${group.name}: max ${group.max} unit(s) allowed, got $totalUnits"
            )
        }
        // maxDistinct different options
        if (group.maxDistinct != null && distinctCount > group.maxDistinct) {
            violations += Violation(
                groupId,
                "${group.name}: at most ${group.maxDistinct} different option(s), got $distinctCount"
            )
        }
        // maxPerOption
        if (group.maxPerOption != null) {
            for (sel in selectedInGroup) {
                if (sel.quantity > group.maxPerOption) {
                    val optName = group.options.find { it.id == sel.optionId }?.name ?: sel.optionId
                    violations += Violation(
                        "${groupId}_${sel.optionId}",
                        "${group.name}: $optName max ${group.maxPerOption} per option"
                    )
                }
            }
        }
    }

    // --- Rule checks (requires / excludes) ---
    val allSelectedOptionIds = item.modifiers.map { it.optionId }.toSet()
    for (rule in catalog.rules) {
        if (rule.optionId !in allSelectedOptionIds) continue
        val optionName = findOptionName(catalog, rule.optionId)
        when (rule.type) {
            RuleType.REQUIRES -> {
                val hasAnyTarget = rule.targetOptionIds.any { it in allSelectedOptionIds }
                if (!hasAnyTarget) {
                    val targetNames = rule.targetOptionIds.mapNotNull { findOptionName(catalog, it) }
                    violations += Violation(
                        rule.id,
                        "$optionName requires at least one of: ${targetNames.joinToString(", ")}"
                    )
                }
            }
            RuleType.EXCLUDES -> {
                val conflicting = rule.targetOptionIds.filter { it in allSelectedOptionIds }
                if (conflicting.isNotEmpty()) {
                    val conflictNames = conflicting.mapNotNull { findOptionName(catalog, it) }
                    violations += Violation(
                        rule.id,
                        "$optionName cannot be combined with: ${conflictNames.joinToString(", ")}"
                    )
                }
            }
        }
    }

    return violations
}

private fun findOptionName(catalog: Catalog, optionId: String): String? =
    catalog.modifierGroups.flatMap { it.options }.find { it.id == optionId }?.name
