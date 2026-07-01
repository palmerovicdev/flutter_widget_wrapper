package com.palmerodev.fww.wrappers

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.palmerodev.fww.model.WidgetWrapper

object WrapperJsonCodec {

    private val PRETTY_GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun parseList(json: String): List<WidgetWrapper> {
        if (json.isBlank()) return emptyList()
        val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptyList()
        val array = when {
            root.isJsonArray -> root.asJsonArray
            else -> return emptyList()
        }
        return array.mapNotNull { element ->
            if (element.isJsonObject) parseWrapper(element.asJsonObject) else null
        }
    }

    fun encodeList(wrappers: List<WidgetWrapper>): String {
        val array = JsonArray()
        for (w in wrappers) array.add(encodeWrapper(w))
        return PRETTY_GSON.toJson(array)
    }

    fun encodeSingle(wrapper: WidgetWrapper): String = PRETTY_GSON.toJson(encodeWrapper(wrapper))

    private fun encodeWrapper(w: WidgetWrapper): JsonObject {
        val obj = JsonObject()
        obj.addProperty("name", w.name)
        w.description?.let { obj.addProperty("description", it) }
        if (w.category.isNotBlank() && w.category != "Custom") obj.addProperty("category", w.category)
        if (!w.enabled) obj.addProperty("enabled", false)
        val templateArr = JsonArray().also { arr -> w.template.forEach { arr.add(it) } }
        obj.add("template", templateArr)
        if (w.allowedParents != listOf("any")) {
            obj.add("allowedParents", JsonArray().also { arr -> w.allowedParents.forEach { arr.add(it) } })
        }
        if (w.disallowedParents.isNotEmpty()) {
            obj.add(
                "disallowedParents",
                JsonArray().also { arr -> w.disallowedParents.forEach { arr.add(it) } },
            )
        }
        if (w.requiresDirectParent) obj.addProperty("requiresDirectParent", true)
        w.warning?.let { obj.addProperty("warning", it) }
        return obj
    }

    private fun parseWrapper(obj: JsonObject): WidgetWrapper? {
        val name = obj.stringOrNull("name") ?: return null
        val template = obj.get("template")?.let(::parseTemplate) ?: return null
        return WidgetWrapper(
            name = name,
            template = template,
            description = obj.stringOrNull("description"),
            category = obj.stringOrNull("category") ?: "Custom",
            enabled = obj.booleanOr("enabled", true),
            allowedParents = obj.stringArrayOr("allowedParents", listOf("any")),
            disallowedParents = obj.stringArrayOr("disallowedParents", emptyList()),
            requiresDirectParent = obj.booleanOr("requiresDirectParent", false),
            warning = obj.stringOrNull("warning"),
        )
    }

    private fun parseTemplate(element: JsonElement): List<String>? = when {
        element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.lines()
        element.isJsonArray -> element.asJsonArray.mapNotNull { it.asStringOrNull() }
        else -> null
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.booleanOr(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: default

    private fun JsonObject.stringArrayOr(key: String, default: List<String>): List<String> {
        val el = get(key) ?: return default
        if (!el.isJsonArray) return default
        return (el as JsonArray).mapNotNull { it.asStringOrNull() }
    }

    private fun JsonElement.asStringOrNull(): String? =
        takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}
