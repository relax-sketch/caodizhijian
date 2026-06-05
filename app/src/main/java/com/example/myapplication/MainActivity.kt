package com.example.myapplication

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.quality.ui.QualityScreen
import com.example.myapplication.quality.ui.QualityCheckViewModel
import com.example.myapplication.quality.ui.web.QualityWebBridge
import com.example.myapplication.quality.ui.web.toWebJson
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: QualityCheckViewModel by viewModels()
    private lateinit var webView: WebView
    private lateinit var bridge: QualityWebBridge

    private val directoryPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::onDirectoryPicked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bridge = QualityWebBridge(
            viewModel = viewModel,
            pickDirectory = { directoryPicker.launch(null) },
        )
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            addJavascriptInterface(bridge, "QualityBridge")
            bridge.attach(this)
            loadUrl("file:///android_asset/quality-web/index.html")
        }
        setContentView(webView)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    bridge.renderState(state.toWebJson())
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val screen = viewModel.uiState.value.screen
                    if (screen == QualityScreen.SOURCE || screen == QualityScreen.PROGRESS) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    } else {
                        viewModel.navigateBack()
                    }
                }
            },
        )
    }
}
