package com.example.myapplication.quality.ui.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.myapplication.quality.review.DetailStatusFilter
import com.example.myapplication.quality.review.QualityTableGroup
import com.example.myapplication.quality.ui.QualityCheckViewModel
import org.json.JSONObject

class QualityWebBridge(
    private val viewModel: QualityCheckViewModel,
    private val pickDirectory: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    @Volatile private var latestStateJson: String = "{}"

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun renderState(json: String) {
        latestStateJson = json
        evaluate("window.QualityApp && window.QualityApp.render($json);")
    }

    @JavascriptInterface
    fun initialState(): String = latestStateJson

    @JavascriptInterface
    fun perform(action: String, payload: String?) {
        mainHandler.post {
            val data = payload?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
            when (action) {
                "pickDirectory" -> pickDirectory()
                "rescan" -> viewModel.scanSavedDirectory()
                "showSource" -> viewModel.showSourceSelection()
                "showScope" -> viewModel.showScopeSelection()
                "showSummary" -> viewModel.showSummary()
                "showSettings" -> viewModel.showSettings()
                "back" -> viewModel.navigateBack()
                "toggleAll" -> viewModel.setCheckAllMode(data.optBoolean("enabled"))
                "toggleTest" -> viewModel.setTestMode(data.optBoolean("enabled"))
                "toggleNational" -> viewModel.setNationalCheckMode(data.optBoolean("enabled"))
                "setCounty" -> viewModel.updateCountyFilter(data.optString("county").ifBlank { null })
                "setQuery" -> viewModel.updatePlotQuery(data.optString("query"))
                "selectPlot" -> selectPlot(data.optString("key"))
                "startCheck" -> viewModel.startCheck()
                "cancelCheck" -> viewModel.cancelCheck()
                "openDetail" -> openDetail(data.optString("key"))
                "recheck" -> viewModel.recheck()
                "setIgnored" -> viewModel.setIgnored(
                    issueFingerprint = data.optString("fingerprint"),
                    ignored = data.optBoolean("ignored"),
                    reason = data.optString("reason").ifBlank { null },
                )
                "setStatusFilter" -> runCatching {
                    viewModel.setDetailStatusFilter(DetailStatusFilter.valueOf(data.optString("value")))
                }
                "setTableFilter" -> runCatching {
                    viewModel.setDetailTableGroup(QualityTableGroup.valueOf(data.optString("value")))
                }
            }
        }
    }

    private fun selectPlot(key: String) {
        val plot = viewModel.uiState.value.filteredPlots.firstOrNull { it.webKey() == key } ?: return
        viewModel.selectPlot(plot)
    }

    private fun openDetail(key: String) {
        val result = viewModel.uiState.value.reviewedRun
            ?.plotResults
            ?.firstOrNull { it.plot.webKey() == key }
            ?: return
        viewModel.showPlotDetails(result)
    }

    private fun evaluate(script: String) {
        mainHandler.post {
            webView?.evaluateJavascript(script, null)
        }
    }
}
