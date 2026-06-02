package com.example.myapplication.quality.ui.web

import com.example.myapplication.quality.domain.CheckIssue
import com.example.myapplication.quality.domain.InvalidZdbSource
import com.example.myapplication.quality.domain.PassedRule
import com.example.myapplication.quality.domain.PlotRef
import com.example.myapplication.quality.domain.SkippedRule
import com.example.myapplication.quality.review.DetailStatusFilter
import com.example.myapplication.quality.review.QualityTableGroup
import com.example.myapplication.quality.review.ReviewedPlotResult
import com.example.myapplication.quality.review.filteredDetail
import com.example.myapplication.quality.rules.RuleSeverity
import com.example.myapplication.quality.ui.QualityCheckUiState
import java.util.Locale
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

internal fun QualityCheckUiState.toWebJson(): String {
    val state = this
    return JSONObject()
        .put("screen", screen.name.lowercase())
        .put("directory", directory?.let { JSONObject().put("name", it.displayName).put("uri", it.uri) } ?: JSONObject.NULL)
        .put("isScanning", isScanning)
        .put("errorMessage", errorMessage ?: JSONObject.NULL)
        .put("scan", JSONObject()
            .put("validCount", scanResult?.validSources?.size ?: 0)
            .put("indexedCount", indexedPlots.size)
            .put("rejectedCount", rejectedSources.size)
            .put("rejected", rejectedSources.toJsonArray { it.toJson() }))
        .put("scope", JSONObject()
            .put("checkAllMode", checkAllMode)
            .put("testMode", testMode)
            .put("nationalCheckMode", nationalCheckMode)
            .put("selectedCounty", selectedCounty ?: JSONObject.NULL)
            .put("plotQuery", plotQuery)
            .put("countyOptions", countyOptions.toJsonArray { it })
            .put("filteredCount", filteredPlots.size)
            .put("indexedCount", indexedPlots.size)
            .put("selectedPlotKey", selectedPlot?.webKey() ?: JSONObject.NULL)
            .put("plots", filteredPlots.take(80).toJsonArray { it.toJson(state.selectedPlot == it) }))
        .put("progress", progress?.let {
            JSONObject()
                .put("completed", it.completedPlots)
                .put("total", it.totalPlots)
                .put("currentPlot", it.currentPlot?.displayPlotId ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        .put("summary", reviewedRun?.let { reviewed ->
            JSONObject()
                .put("cancelled", reviewed.sourceRun.cancelled)
                .put("checkedPlots", reviewed.summary.checkedPlots)
                .put("pendingMandatory", reviewed.summary.pendingMandatoryIssues)
                .put("pendingAdvisory", reviewed.summary.pendingAdvisoryIssues)
                .put("ignored", reviewed.summary.ignoredIssues)
                .put("skipped", reviewed.summary.skippedRules)
                .put("passed", reviewed.summary.passedRules)
                .put("plots", reviewed.plotResults.toJsonArray { it.toSummaryJson() })
        } ?: JSONObject.NULL)
        .put("detail", detailPlot?.toDetailJson(detailStatusFilter, detailTableGroup, testMode) ?: JSONObject.NULL)
        .put("filters", JSONObject()
            .put("status", detailStatusFilter.name)
            .put("table", detailTableGroup.name)
            .put("statusOptions", DetailStatusFilter.values().toList().toJsonArray {
                JSONObject().put("name", it.name).put("label", it.label)
            })
            .put("tableOptions", QualityTableGroup.values().toList().toJsonArray {
                JSONObject().put("name", it.name).put("label", it.label)
            }))
        .toString()
}

internal fun PlotRef.webKey(): String = "${source.uri}|${plotTable.name}|$displayPlotId|$rawPlotId"

private fun InvalidZdbSource.toJson(): JSONObject =
    JSONObject()
        .put("name", displayName)
        .put("reason", reason)

private fun PlotRef.toJson(selected: Boolean): JSONObject =
    JSONObject()
        .put("key", webKey())
        .put("id", displayPlotId)
        .put("county", countyLabel ?: "未知区县")
        .put("source", source.projectName)
        .put("table", plotTable.tableName)
        .put("selected", selected)

private fun ReviewedPlotResult.toSummaryJson(): JSONObject =
    JSONObject()
        .put("key", plot.webKey())
        .put("id", plot.displayPlotId)
        .put("county", plot.countyLabel ?: "未知区县")
        .put("mandatory", pendingMandatory.size)
        .put("advisory", pendingAdvisory.size)
        .put("ignored", ignored.size)
        .put("skipped", skippedRules.size)
        .put("passed", passedRules.size)

private fun ReviewedPlotResult.toDetailJson(
    statusFilter: DetailStatusFilter,
    tableGroup: QualityTableGroup,
    includePassed: Boolean,
): JSONObject {
    val filtered = filteredDetail(statusFilter, tableGroup, includePassed)
    return JSONObject()
        .put("plotId", plot.displayPlotId)
        .put("county", plot.countyLabel ?: "未知区县")
        .put("mandatory", pendingMandatory.size)
        .put("advisory", pendingAdvisory.size)
        .put("ignoredCount", ignored.size)
        .put("counts", JSONObject()
            .put("all", filtered.counts.all)
            .put("failed", filtered.counts.failed)
            .put("mandatory", filtered.counts.mandatory)
            .put("advisory", filtered.counts.advisory)
            .put("ignored", filtered.counts.ignored))
        .put("issues", (filtered.pendingMandatory + filtered.pendingAdvisory + filtered.ignored).toJsonArray { issue ->
            issue.toJson(ignored = issue.ignored || ignored.any { it.fingerprint == issue.fingerprint })
        })
        .put("skippedRules", filtered.skippedRules.toJsonArray { it.toJson() })
        .put("passedRules", filtered.passedRules.toJsonArray { it.toJson() })
}

private fun CheckIssue.toJson(ignored: Boolean): JSONObject =
    JSONObject()
        .put("fingerprint", fingerprint)
        .put("severity", severity.webLabel())
        .put("severityKind", severity.name)
        .put("ruleId", ruleId)
        .put("tableName", tableName)
        .put("title", title)
        .put("explanation", explanation)
        .put("foundValue", foundValueText())
        .put("ignored", ignored)
        .put("ignoredReason", ignoredReason ?: JSONObject.NULL)

private fun SkippedRule.toJson(): JSONObject =
    JSONObject()
        .put("severity", severity.webLabel())
        .put("severityKind", severity.name)
        .put("ruleId", ruleId)
        .put("tableName", tableName)
        .put("title", title)
        .put("explanation", reason)
        .put("foundValue", "无法执行")
        .put("ignored", false)

private fun PassedRule.toJson(): JSONObject =
    JSONObject()
        .put("severity", "通过")
        .put("severityKind", "PASSED")
        .put("ruleId", ruleId)
        .put("tableName", tableName)
        .put("title", title)
        .put("explanation", explanation)
        .put("foundValue", "PASS")
        .put("ignored", false)

private fun RuleSeverity.webLabel(): String =
    when (this) {
        RuleSeverity.MANDATORY -> "强制性"
        RuleSeverity.ADVISORY -> "提示性"
    }

private fun CheckIssue.foundValueText(): String {
    val locationText = locationValues.toLocationText()
    val actualText = actualValues.toFoundValueText(ruleId)
    return listOf(locationText, actualText)
        .filter { it.isNotBlank() && it != "NULL" }
        .joinToString("；")
        .ifBlank { "NULL" }
}

private fun Map<String, String?>.toFoundValueText(ruleId: String): String =
    if (isEmpty()) {
        "NULL"
    } else {
        if (ruleId == "ADD_GRASS_039") {
            distanceFoundValueText()?.let { return it }
        }
        val meaningfulEntries = entries
            .filterNot { (name, _) -> name.equals("PK_UID", ignoreCase = true) }
            .let { filtered ->
                val namedValues = filtered.filter { (name, _) -> name.any(Char::isLetter).not() || name.any { it.code > 127 } }
                namedValues.ifEmpty { filtered.take(3) }
            }
        meaningfulEntries.joinToString("；") { (name, value) ->
            "$name：${value.formatDisplayValue()}"
        }
    }

private fun Map<String, String?>.toLocationText(): String =
    entries
        .filterNot { (name, _) -> name.equals("YD_ID", ignoreCase = true) }
        .joinToString("；") { (name, value) ->
            "${name.locationDisplayName()}：${value.formatDisplayValue()}"
        }

private fun String.locationDisplayName(): String =
    when (uppercase()) {
        "YX_ID" -> "样线号"
        "YF_ID" -> "样方号"
        "ZW_MC" -> "植物名称"
        "MC" -> "名称"
        else -> this
    }

private fun Map<String, String?>.distanceFoundValueText(): String? {
    val lineX = this["样线终点 X"]?.toDoubleOrNull() ?: return null
    val lineY = this["样线终点 Y"]?.toDoubleOrNull() ?: return null
    val plotX = this["样地中心 X"]?.toDoubleOrNull() ?: return null
    val plotY = this["样地中心 Y"]?.toDoubleOrNull() ?: return null
    val dx = lineX - plotX
    val dy = lineY - plotY
    val distance = sqrt(dx * dx + dy * dy)
    return listOf(
        "相差距离：${distance.formatMeters()}米",
        "样线终点 X：${lineX.formatCoordinate()}",
        "样线终点 Y：${lineY.formatCoordinate()}",
        "样地中心 X：${plotX.formatCoordinate()}",
        "样地中心 Y：${plotY.formatCoordinate()}",
    ).joinToString("；")
}

private fun String?.formatDisplayValue(): String {
    val raw = this?.ifBlank { null } ?: return "NULL"
    return raw.toDoubleOrNull()?.formatCoordinate() ?: raw
}

private fun Double.formatMeters(): String =
    String.format(Locale.US, "%.2f", this)

private fun Double.formatCoordinate(): String =
    String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

private fun <T> Iterable<T>.toJsonArray(transform: (T) -> Any): JSONArray {
    val array = JSONArray()
    forEach { array.put(transform(it)) }
    return array
}
