package com.tekinflow.demo.data.local.converter

import androidx.room.TypeConverter
import com.tekinflow.demo.domain.model.MaterialStatus
import com.tekinflow.demo.domain.model.MaterialUnit
import com.tekinflow.demo.domain.model.PurchaseRequestItem
import com.tekinflow.demo.domain.model.UrgencyLevel
import com.tekinflow.demo.domain.model.UserRole
import org.json.JSONArray
import org.json.JSONObject

class Converters {

    @TypeConverter
    fun fromUrgencyLevel(value: UrgencyLevel?): String? = value?.name

    @TypeConverter
    fun toUrgencyLevel(value: String?): UrgencyLevel? =
        value?.let { runCatching { UrgencyLevel.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromMaterialStatus(value: MaterialStatus?): String? = value?.name

    @TypeConverter
    fun toMaterialStatus(value: String?): MaterialStatus? =
        value?.let { runCatching { MaterialStatus.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromMaterialUnit(value: MaterialUnit?): String? = value?.name

    @TypeConverter
    fun toMaterialUnit(value: String?): MaterialUnit? =
        value?.let { runCatching { MaterialUnit.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromUserRole(value: UserRole?): String? = value?.name

    @TypeConverter
    fun toUserRole(value: String?): UserRole? =
        value?.let { runCatching { UserRole.valueOf(it) }.getOrNull() }

    @TypeConverter
    fun fromStringSet(value: Set<String>?): String {
        return value
            ?.filter { it.isNotBlank() }
            ?.joinToString(",")
            .orEmpty()
    }

    @TypeConverter
    fun toStringSet(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        return value.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    @TypeConverter
    fun fromRequestItems(value: List<PurchaseRequestItem>?): String {
        val array = JSONArray()
        value.orEmpty().forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("itemName", item.itemName)
                    .put("quantity", item.quantity)
                    .put("unit", item.unit.name)
                    .put("packageCount", item.packageCount)
                    .put("dimensionsNote", item.dimensionsNote)
                    .put("sortOrder", item.sortOrder)
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun toRequestItems(value: String?): List<PurchaseRequestItem> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(value)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                PurchaseRequestItem(
                    id = item.optString("id"),
                    itemName = item.optString("itemName"),
                    quantity = item.optString("quantity", "1"),
                    unit = runCatching {
                        MaterialUnit.valueOf(item.optString("unit", MaterialUnit.ADET.name))
                    }.getOrDefault(MaterialUnit.ADET),
                    packageCount = item.optInt("packageCount", 0),
                    dimensionsNote = item.optString("dimensionsNote"),
                    sortOrder = item.optInt("sortOrder", index)
                )
            }
        }.getOrDefault(emptyList())
    }
}
