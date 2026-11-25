package com.devsstream.caffepos.data.repository

import com.devsstream.caffepos.core.model.Catalog
import com.devsstream.caffepos.data.api.FakeApi
import com.devsstream.caffepos.data.db.CatalogCacheEntity
import com.devsstream.caffepos.data.db.CatalogDao
import com.devsstream.caffepos.data.db.ServerTimeDao
import com.devsstream.caffepos.data.db.ServerTimeOffsetEntity
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val fakeApi: FakeApi,
    private val catalogDao: CatalogDao,
    private val serverTimeDao: ServerTimeDao
) {
    suspend fun getCatalog(): Catalog {
        return try {
            val response = fakeApi.getCatalog()
            // Update server time offset
            val offsetMs = response.serverTime.toEpochMilli() - System.currentTimeMillis()
            serverTimeDao.saveOffset(
                ServerTimeOffsetEntity(offsetMs = offsetMs, fetchedAtMs = System.currentTimeMillis())
            )
            // Cache catalog
            val catalogJson = catalogToJson(response.catalog)
            catalogDao.saveCatalog(
                CatalogCacheEntity(
                    version = response.version,
                    catalogJson = catalogJson,
                    fetchedAtMs = System.currentTimeMillis()
                )
            )
            response.catalog
        } catch (e: Exception) {
            // Offline: use cached
            val cached = catalogDao.getCachedCatalog()
                ?: throw IllegalStateException("No catalog available offline and no cache")
            jsonToCatalog(cached.catalogJson)
        }
    }

    // Simple JSON serialization for catalog caching
    private fun catalogToJson(catalog: Catalog): String = JSONObject().apply {
        put("version", catalog.version)
        put("products", JSONArray(catalog.products.map { p ->
            JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("category", p.category.name)
                put("basePriceBdt", p.basePriceBdt)
                put("sizes", JSONArray(p.sizes.map { s ->
                    JSONObject().apply {
                        put("id", s.id); put("label", s.label); put("extraPriceBdt", s.extraPriceBdt)
                    }
                }))
                put("modifierGroupIds", JSONArray(p.modifierGroupIds))
            }
        }))
        put("groups", JSONArray(catalog.modifierGroups.map { g ->
            JSONObject().apply {
                put("id", g.id); put("name", g.name)
                if (g.max != null) put("max", g.max)
                if (g.maxDistinct != null) put("maxDistinct", g.maxDistinct)
                if (g.maxPerOption != null) put("maxPerOption", g.maxPerOption)
                put("options", JSONArray(g.options.map { o ->
                    JSONObject().apply {
                        put("id", o.id); put("name", o.name); put("priceBdt", o.priceBdt)
                    }
                }))
            }
        }))
        put("rules", JSONArray(catalog.rules.map { r ->
            JSONObject().apply {
                put("id", r.id); put("type", r.type.name)
                put("optionId", r.optionId)
                put("targetOptionIds", JSONArray(r.targetOptionIds))
            }
        }))
    }.toString()

    private fun jsonToCatalog(json: String): Catalog {
        val obj = JSONObject(json)
        val version = obj.getInt("version")
        val products = (0 until obj.getJSONArray("products").length()).map { i ->
            val p = obj.getJSONArray("products").getJSONObject(i)
            val sizes = (0 until p.getJSONArray("sizes").length()).map { j ->
                val s = p.getJSONArray("sizes").getJSONObject(j)
                com.devsstream.caffepos.core.model.ProductSize(s.getString("id"), s.getString("label"), s.getInt("extraPriceBdt"))
            }
            val groupIds = (0 until p.getJSONArray("modifierGroupIds").length()).map { j ->
                p.getJSONArray("modifierGroupIds").getString(j)
            }
            com.devsstream.caffepos.core.model.Product(
                p.getString("id"), p.getString("name"),
                com.devsstream.caffepos.core.model.ProductCategory.valueOf(p.getString("category")),
                p.getInt("basePriceBdt"), sizes, groupIds
            )
        }
        val groups = (0 until obj.getJSONArray("groups").length()).map { i ->
            val g = obj.getJSONArray("groups").getJSONObject(i)
            val opts = (0 until g.getJSONArray("options").length()).map { j ->
                val o = g.getJSONArray("options").getJSONObject(j)
                com.devsstream.caffepos.core.model.ModifierOption(o.getString("id"), o.getString("name"), o.getInt("priceBdt"))
            }
            com.devsstream.caffepos.core.model.ModifierGroup(
                g.getString("id"), g.getString("name"),
                if (g.has("max")) g.getInt("max") else null,
                if (g.has("maxDistinct")) g.getInt("maxDistinct") else null,
                if (g.has("maxPerOption")) g.getInt("maxPerOption") else null,
                opts
            )
        }
        val rules = (0 until obj.getJSONArray("rules").length()).map { i ->
            val r = obj.getJSONArray("rules").getJSONObject(i)
            val targets = (0 until r.getJSONArray("targetOptionIds").length()).map { j ->
                r.getJSONArray("targetOptionIds").getString(j)
            }
            com.devsstream.caffepos.core.model.ModifierRule(
                r.getString("id"),
                com.devsstream.caffepos.core.model.RuleType.valueOf(r.getString("type")),
                r.getString("optionId"), targets
            )
        }
        return Catalog(version, products, groups, rules,
            listOf(
                com.devsstream.caffepos.core.model.Promotion("P1", com.devsstream.caffepos.core.model.PromotionType.BAKERY_3_FOR_2),
                com.devsstream.caffepos.core.model.Promotion("P2", com.devsstream.caffepos.core.model.PromotionType.HAPPY_HOUR_BEVERAGE)
            ))
    }
}
