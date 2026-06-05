package com.example.myapplication.quality.rules

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class RuleSetValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

object EmbeddedRuleSetParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parse(json: String): EmbeddedRuleSet {
        val root = try {
            JSONObject(json)
        } catch (exception: JSONException) {
            throw RuleSetValidationException("Rule set is not valid JSON.", exception)
        }

        return try {
            parseRoot(root)
        } catch (exception: RuleSetValidationException) {
            throw exception
        } catch (exception: JSONException) {
            throw RuleSetValidationException("Rule set structure is invalid: ${exception.message}", exception)
        }
    }

    private fun parseRoot(root: JSONObject): EmbeddedRuleSet {
        val schemaVersion = root.requireInt("schemaVersion", "rule set")
        requireValue(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
            "Unsupported schemaVersion '$schemaVersion'. Expected $SUPPORTED_SCHEMA_VERSION."
        }

        val sources = root.requireArray("sources", "rule set").mapObjects("sources") { source ->
            RuleSourceMetadata(
                id = source.requireText("id", "source"),
                kind = source.requireEnum("kind", "source"),
                label = source.requireText("label", "source"),
                description = source.requireText("description", "source"),
            )
        }.requireNonEmpty("sources")
        requireUnique(sources.map { it.id }, "source id")

        val sourceIds = sources.mapTo(mutableSetOf()) { it.id }
        val rules = root.requireArray("rules", "rule set").mapObjects("rules") { rule ->
            val targetTable = rule.requireText("targetTable", "rule")
            val requiredTables = rule.requireTextArray("requiredTables", "rule").requireNonEmpty("requiredTables")
            requireValue(targetTable in requiredTables) {
                "Rule targetTable '$targetTable' must be included in requiredTables."
            }

            EmbeddedRule(
                id = rule.requireText("id", "rule"),
                sourceId = rule.requireText("sourceId", "rule"),
                severity = rule.requireEnum("severity", "rule"),
                targetTable = targetTable,
                title = rule.requireText("title", "rule"),
                explanation = rule.requireText("explanation", "rule"),
                requiredTables = requiredTables,
                requiredFields = rule.requireTextArray("requiredFields", "rule").requireNonEmpty("requiredFields"),
                locatorFields = rule.requireTextArray("locatorFields", "rule").requireNonEmpty("locatorFields"),
                sql = rule.requireText("sql", "rule"),
                enabled = rule.optionalBoolean("enabled", "rule", default = true),
            )
        }.requireNonEmpty("rules")

        requireUnique(rules.map { it.id }, "rule id")
        rules.forEach { rule ->
            requireValue(rule.sourceId in sourceIds) {
                "Rule '${rule.id}' references unknown sourceId '${rule.sourceId}'."
            }
        }

        return EmbeddedRuleSet(
            schemaVersion = schemaVersion,
            ruleSetVersion = root.requireText("ruleSetVersion", "rule set"),
            publishedAt = root.requireText("publishedAt", "rule set"),
            sources = sources,
            rules = rules,
        )
    }

    private fun JSONObject.requireArray(name: String, context: String): JSONArray =
        optJSONArray(name) ?: invalid("$context must contain array '$name'.")

    private fun JSONObject.requireText(name: String, context: String): String {
        val value = optString(name, "").trim()
        requireValue(value.isNotEmpty()) { "$context must contain non-blank '$name'." }
        return value
    }

    private fun JSONObject.requireInt(name: String, context: String): Int {
        requireValue(has(name)) { "$context must contain integer '$name'." }
        return try {
            getInt(name)
        } catch (exception: JSONException) {
            invalid("$context must contain integer '$name'.", exception)
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.requireEnum(name: String, context: String): T {
        val value = requireText(name, context)
        return enumValues<T>().firstOrNull { it.name == value }
            ?: invalid("$context has unsupported '$name' value '$value'.")
    }

    private fun JSONObject.requireTextArray(name: String, context: String): List<String> {
        val values = requireArray(name, context).mapStrings(name)
        requireUnique(values, "$context $name entry")
        return values
    }

    private fun JSONObject.optionalBoolean(name: String, context: String, default: Boolean): Boolean {
        if (!has(name)) {
            return default
        }
        val value = get(name)
        return value as? Boolean ?: invalid("$context must contain boolean '$name'.")
    }

    private inline fun <T> JSONArray.mapObjects(name: String, transform: (JSONObject) -> T): List<T> =
        buildList {
            for (index in 0 until length()) {
                val value = optJSONObject(index) ?: invalid("$name[$index] must be an object.")
                add(transform(value))
            }
        }

    private fun JSONArray.mapStrings(name: String): List<String> =
        buildList {
            for (index in 0 until length()) {
                val value = optString(index, "").trim()
                requireValue(value.isNotEmpty()) { "$name[$index] must be non-blank." }
                add(value)
            }
        }

    private fun <T> List<T>.requireNonEmpty(name: String): List<T> {
        requireValue(isNotEmpty()) { "'$name' must not be empty." }
        return this
    }

    private fun requireUnique(values: List<String>, name: String) {
        val duplicate = values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.key
        requireValue(duplicate == null) { "Duplicate $name '$duplicate'." }
    }

    private inline fun requireValue(condition: Boolean, message: () -> String) {
        if (!condition) {
            invalid(message())
        }
    }

    private fun invalid(message: String, cause: Throwable? = null): Nothing =
        throw RuleSetValidationException(message, cause)
}
