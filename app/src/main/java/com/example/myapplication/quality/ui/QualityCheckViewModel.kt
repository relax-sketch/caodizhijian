package com.example.myapplication.quality.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.quality.annotations.SQLiteIssueAnnotationStore
import com.example.myapplication.quality.check.PlotRuleExecutor
import com.example.myapplication.quality.check.QualityCheckEngine
import com.example.myapplication.quality.data.AndroidDataDirectoryStore
import com.example.myapplication.quality.data.AndroidZdbSourceScanner
import com.example.myapplication.quality.data.SafPlotIndexRepository
import com.example.myapplication.quality.data.SafReadOnlyZdbAccess
import com.example.myapplication.quality.domain.CheckScope
import com.example.myapplication.quality.domain.DataDirectoryRef
import com.example.myapplication.quality.domain.DefaultCheckScopeSelector
import com.example.myapplication.quality.domain.PlotRef
import com.example.myapplication.quality.domain.ZdbScanResult
import com.example.myapplication.quality.review.IssueReviewService
import com.example.myapplication.quality.review.DetailStatusFilter
import com.example.myapplication.quality.review.QualityTableGroup
import com.example.myapplication.quality.review.ReviewedCheckRun
import com.example.myapplication.quality.review.ReviewedPlotResult
import com.example.myapplication.quality.rules.AssetRuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QualityCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val directoryStore = AndroidDataDirectoryStore(application)
    private val sourceScanner = AndroidZdbSourceScanner(application.contentResolver)
    private val databaseAccess = SafReadOnlyZdbAccess(application)
    private val plotIndexRepository = SafPlotIndexRepository(databaseAccess)
    private val scopeSelector = DefaultCheckScopeSelector()
    private val engine = QualityCheckEngine(
        ruleRepository = AssetRuleRepository(application.assets),
        plotRuleExecutor = PlotRuleExecutor(databaseAccess),
    )
    private val reviewService = IssueReviewService(SQLiteIssueAnnotationStore(application))

    private val _uiState = MutableStateFlow(
        QualityCheckUiState(directory = directoryStore.savedDirectory()),
    )
    val uiState: StateFlow<QualityCheckUiState> = _uiState.asStateFlow()

    private var runJob: Job? = null
    @Volatile private var cancelRequested = false

    fun onDirectoryPicked(uri: Uri) {
        try {
            val directory = directoryStore.persistPickedDirectory(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            scan(directory)
        } catch (exception: Exception) {
            setError("无法读取选择的目录：${exception.message.orEmpty()}")
        }
    }

    fun scanSavedDirectory() {
        val directory = _uiState.value.directory ?: return
        scan(directory)
    }

    fun showSourceSelection() {
        _uiState.update { it.copy(screen = QualityScreen.SOURCE, detailPlot = null, errorMessage = null) }
    }

    fun showScopeSelection() {
        if (_uiState.value.indexedPlots.isNotEmpty()) {
            _uiState.update { it.copy(screen = QualityScreen.SCOPE, detailPlot = null, errorMessage = null) }
        }
    }

    fun updateCountyFilter(county: String?) {
        if (_uiState.value.checkAllMode) return
        directoryStore.saveCountyLabel(county)
        _uiState.update { state ->
            filter(state, selectedCounty = county, plotQuery = state.plotQuery)
        }
    }

    fun updatePlotQuery(query: String) {
        if (_uiState.value.checkAllMode) return
        _uiState.update { state ->
            filter(state, selectedCounty = state.selectedCounty, plotQuery = query)
        }
    }

    fun selectPlot(plot: PlotRef) {
        if (_uiState.value.checkAllMode) return
        _uiState.update {
            it.copy(
                selectedPlot = plot,
                selectedScope = scopeSelector.single(plot),
                errorMessage = null,
            )
        }
    }

    fun chooseSingleScope() {
        val selectedPlot = _uiState.value.selectedPlot
        if (selectedPlot == null) {
            setError("请先选择一个样地。")
        } else {
            _uiState.update { it.copy(selectedScope = scopeSelector.single(selectedPlot), errorMessage = null) }
        }
    }

    fun chooseCountyScope() {
        val selectedCounty = _uiState.value.selectedCounty
        if (selectedCounty.isNullOrBlank()) {
            setError("请选择一个区县后再进行区县批量质检。")
            return
        }
        val scope = scopeSelector.county(_uiState.value.indexedPlots, selectedCounty)
        _uiState.update { it.copy(selectedScope = scope, errorMessage = null) }
    }

    fun chooseAllScope() {
        _uiState.update {
            it.copy(selectedScope = scopeSelector.all(it.indexedPlots), errorMessage = null)
        }
    }

    fun setTestMode(enabled: Boolean) {
        _uiState.update { it.copy(testMode = enabled) }
    }

    fun setNationalCheckMode(enabled: Boolean) {
        _uiState.update { it.copy(nationalCheckMode = enabled) }
    }

    fun setCheckAllMode(enabled: Boolean) {
        _uiState.update { state ->
            if (enabled) {
                state.withCheckAllMode(enabled = true)
            } else {
                val restoredCounty = scopeSelector.restoreCounty(state.selectedCounty, state.countyOptions)
                state.withCheckAllMode(enabled = false, restoredCounty = restoredCounty)
            }
        }
    }

    fun startCheck() {
        val state = _uiState.value
        val scope = if (state.checkAllMode) {
            scopeSelector.all(state.indexedPlots)
        } else {
            state.selectedPlot?.let(scopeSelector::single)
        }
        if (scope == null || scope.plots.isEmpty()) {
            setError(if (state.checkAllMode) "当前质检范围没有样地。" else "请先选择一个样地。")
            return
        }

        runCheck(scope)
    }

    private fun runCheck(scope: CheckScope) {
        val includeBaselineRules = _uiState.value.nationalCheckMode
        cancelRequested = false
        runJob?.cancel()
        _uiState.update {
            it.copy(
                selectedScope = scope,
                screen = QualityScreen.PROGRESS,
                progress = null,
                reviewedRun = null,
                detailPlot = null,
                errorMessage = null,
            )
        }
        runJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                engine.check(
                    scope = scope,
                    includeBaselineRules = includeBaselineRules,
                    onProgress = { progress -> _uiState.update { it.copy(progress = progress) } },
                    isCancelled = { cancelRequested },
                )
            }.onSuccess { run ->
                val reviewed = reviewService.review(run)
                val singleDetail = if (run.scope is CheckScope.Single) {
                    reviewed.plotResults.singleOrNull()
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        screen = if (singleDetail == null) QualityScreen.SUMMARY else screenAfterCompletedCheck(run.scope),
                        reviewedRun = reviewed,
                        detailPlot = singleDetail,
                        detailStatusFilter = DetailStatusFilter.ALL,
                        detailTableGroup = QualityTableGroup.ALL,
                        progress = null,
                    )
                }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        screen = QualityScreen.SCOPE,
                        progress = null,
                        errorMessage = "质检未完成：${exception.message.orEmpty()}",
                    )
                }
            }
        }
    }

    fun cancelCheck() {
        cancelRequested = true
    }

    fun showPlotDetails(result: ReviewedPlotResult) {
        _uiState.update {
            it.copy(
                screen = QualityScreen.DETAIL,
                detailPlot = result,
                detailStatusFilter = DetailStatusFilter.ALL,
                detailTableGroup = QualityTableGroup.ALL,
            )
        }
    }

    fun showSummary() {
        if (_uiState.value.checkAllMode) {
            _uiState.update { it.copy(screen = QualityScreen.SUMMARY, detailPlot = null) }
        }
    }

    fun showSettings() {
        _uiState.update { it.copy(screen = QualityScreen.SETTINGS, detailPlot = null, errorMessage = null) }
    }

    fun navigateBack() {
        when (_uiState.value.screen) {
            QualityScreen.SOURCE -> Unit
            QualityScreen.PROGRESS -> Unit
            QualityScreen.SCOPE -> showSourceSelection()
            QualityScreen.SUMMARY -> showScopeSelection()
            QualityScreen.SETTINGS -> showSourceSelection()
            QualityScreen.DETAIL -> {
                when (screenAfterDetailBack(_uiState.value.reviewedRun?.sourceRun?.scope)) {
                    QualityScreen.SCOPE -> showScopeSelection()
                    QualityScreen.SUMMARY -> showSummary()
                    else -> Unit
                }
            }
        }
    }

    fun recheck() {
        val previousScope = _uiState.value.reviewedRun?.sourceRun?.scope ?: _uiState.value.selectedScope
        if (previousScope != null) {
            runCheck(previousScope)
        }
    }

    fun setIgnored(issueFingerprint: String, ignored: Boolean, reason: String? = null) {
        val current = _uiState.value.reviewedRun ?: return
        val issue = current.plotResults
            .flatMap { it.pendingMandatory + it.pendingAdvisory + it.ignored }
            .firstOrNull { it.fingerprint == issueFingerprint } ?: return
        if (ignored) {
            val trimmedReason = reason?.trim().orEmpty()
            if (trimmedReason.isBlank()) return
            reviewService.ignore(issue, trimmedReason)
        } else {
            reviewService.cancelIgnore(issue)
        }
        updateReview(reviewService.review(current.sourceRun))
    }

    fun setDetailStatusFilter(filter: DetailStatusFilter) {
        _uiState.update { it.copy(detailStatusFilter = filter) }
    }

    fun setDetailTableGroup(group: QualityTableGroup) {
        _uiState.update { it.copy(detailTableGroup = group) }
    }

    private fun scan(directory: DataDirectoryRef) {
        _uiState.update {
            it.copy(
                directory = directory,
                isScanning = true,
                errorMessage = null,
                scanResult = null,
                indexedPlots = emptyList(),
                filteredPlots = emptyList(),
                selectedPlot = null,
                selectedScope = null,
                checkAllMode = false,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val scanResult = sourceScanner.scan(directory)
                scanResult to plotIndexRepository.index(scanResult.validSources)
            }.onSuccess { (scanResult, indexResult) ->
                val indexedPlots = indexResult.plots
                val countyOptions = scopeSelector.countyOptions(indexedPlots)
                val selectedCounty = scopeSelector.restoreCounty(directoryStore.savedCountyLabel(), countyOptions)
                _uiState.update {
                    it.copy(
                        screen = QualityScreen.SOURCE,
                        scanResult = scanResult,
                        indexedPlots = indexedPlots,
                        rejectedSources = scanResult.invalidSources + indexResult.rejectedSources,
                        countyOptions = countyOptions,
                        selectedCounty = selectedCounty,
                        plotQuery = "",
                        filteredPlots = scopeSelector.filterPlots(indexedPlots, selectedCounty, ""),
                        selectedPlot = null,
                        selectedScope = null,
                        checkAllMode = false,
                        isScanning = false,
                        errorMessage = if (indexedPlots.isEmpty()) "未发现可质检的样地数据。" else null,
                    )
                }
            }.onFailure { exception ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "扫描失败：${exception.message.orEmpty()}",
                    )
                }
            }
        }
    }

    private fun filter(
        state: QualityCheckUiState,
        selectedCounty: String?,
        plotQuery: String,
    ): QualityCheckUiState =
        state.copy(
            selectedCounty = selectedCounty,
            plotQuery = plotQuery,
            filteredPlots = scopeSelector.filterPlots(state.indexedPlots, selectedCounty, plotQuery),
            selectedPlot = null,
            selectedScope = null,
            errorMessage = null,
        )

    private fun updateReview(reviewed: ReviewedCheckRun) {
        _uiState.update { state ->
            val details = state.detailPlot?.plot?.let { displayedPlot ->
                reviewed.plotResults.firstOrNull { it.plot == displayedPlot }
            }
            state.copy(reviewedRun = reviewed, detailPlot = details)
        }
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
