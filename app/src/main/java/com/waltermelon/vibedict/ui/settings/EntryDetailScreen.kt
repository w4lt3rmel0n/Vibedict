package com.waltermelon.vibedict.ui.settings

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.waltermelon.vibedict.R
import com.waltermelon.vibedict.data.DictionaryManager
import com.waltermelon.vibedict.ui.wordresults.CustomWebView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(navController: NavController, dictId: String, word: String) {
    val dictionary = remember(dictId) { DictionaryManager.getDictionaryById(dictId) }
    var showSource by remember { mutableStateOf(false) }
    
    // We need to fetch the content. 
    // DictionaryManager.lookup normally returns a list of results (strings).
    // We want specifically the one from this dictionary.
    // However, lookup() is generic.
    // We'll use ProduceState to fetch data asynchronously.
    
    val contentState = produceState<String?>(initialValue = null, key1 = dictId, key2 = word) {
        val results = DictionaryManager.lookup(dictId, word)
        // results is List<String>?
        // We join them if multiple, or just show the first one. 
        // Usually definitions are HTML parts.
        value = results?.joinToString("<hr/>")
    }

    val content = contentState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(word) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showSource = !showSource }) {
                        Icon(
                            imageVector = if (showSource) Icons.Outlined.Web else Icons.Outlined.Code,
                            contentDescription = "Toggle Source"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (content == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                if (showSource) {
                    SelectionContainer {
                        Text(
                            text = content,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        )
                    }
                } else {
                    // Using a simplified WebView to render content
                    val context = LocalContext.current
                    val htmlContent = remember(content) {
                        // Wrap with basic HTML structure + CSS if available
                        // We might want to reuse HtmlGenerator logic but simplifies for now
                        """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                               /* Basic Reset */
                               body { padding: 16px; margin: 0; font-family: sans-serif; }
                               img { max-width: 100%; height: auto; }
                               ${dictionary?.defaultCssContent ?: ""}
                            </style>
                        </head>
                        <body>
                            $content
                            <script>
                                ${dictionary?.defaultJsContent ?: ""}
                            </script>
                        </body>
                        </html>
                        """.trimIndent()
                    }
    
                    AndroidView(
                        factory = { ctx ->
                            CustomWebView(ctx).apply {
                                // Basic settings
                               settings.javaScriptEnabled = true
                               // We need to handle image loading via custom client if needed, 
                               // but for now let's assumes basic loadDataWithBaseURL handles it 
                               // if we had a proper base URL scheme for mdd.
                               // Actually, standard WebView won't load mdd:// resources without interception.
                               // DictionaryManager.lookup might return HTML with mdd:// images.
                               // The main DefScreen uses a transparent proxy or custom scheme handler.
                               // Reusing the CustomWebView which might have some logic, 
                               // but the WebViewClient is what handles the interception.
                               // We should probably just display text for now as per "Entry Viewer" request 
                               // or try to match DefScreen's capabilities later.
                               // The user screenshot just shows "<content>" text, so maybe simple is enough.
                            }
                        },
                        update = { webView ->
                            webView.loadDataWithBaseURL("http://localhost/", htmlContent, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
