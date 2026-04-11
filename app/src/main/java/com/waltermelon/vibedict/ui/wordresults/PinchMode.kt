package com.waltermelon.vibedict.ui.wordresults

import android.content.Context
import android.util.Log
import java.net.URLEncoder

/**
 * Data class to hold Material You theme colors for HTML generation.
 */
data class ThemeColors(
    val background: String,
    val onBackground: String,
    val primary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val outline: String,
    val onSurface: String,
    val surface: String
)

/**
 * Generates HTML for the full-page WebView rendering mode.
 * Each dictionary entry is wrapped in an iframe for font isolation.
 */
object WebViewModeRenderer {

    /**
     * Generate the full HTML page containing all dictionary entries.
     */
    fun generateFullPageHtml(
        entries: List<DictionaryEntry>,
        expandedStates: Map<String, Boolean>,
        selectedIndices: Map<String, Int>,
        fontPathsMap: Map<String, String>,
        isDarkTheme: Boolean,
        displayScale: Float,
        context: Context,
        themeColors: ThemeColors
    ): String {
        val zoomPercent = ((displayScale + 0.5f) * 100).toInt()

        val entriesHtml = entries.mapIndexed { index, entry ->
            val isExpanded = expandedStates[entry.dictionaryName] ?: entry.isExpandedByDefault
            val selectedIndex = selectedIndices[entry.id] ?: 0
            val customFontPaths = fontPathsMap[entry.id] ?: entry.customFontPaths

            val contentToShow = if (entry.entries.isNotEmpty()) {
                entry.entries.getOrElse(selectedIndex) { entry.entries[0] }
            } else ""

            Log.d("WebViewModeRenderer", "Rendering entry: ${entry.dictionaryName}, Content length: ${contentToShow.length}, Sample: ${contentToShow.take(200).replace("\n", " ")}")

            generateEntrySection(
                entry = entry,
                content = contentToShow,
                customFontPaths = customFontPaths,
                isExpanded = isExpanded,
                isDarkTheme = isDarkTheme,
                forceOriginalStyle = entry.forceOriginalStyle,
                entryIndex = index,
                selectedIndex = selectedIndex,
                themeColors = themeColors
            )
        }.joinToString("\n")

        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=10.0, user-scalable=yes">
    <style>
        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }
        
        :root {
            --bg-color: ${themeColors.background};
            --surface-color: ${themeColors.surface};
            --text-color: ${themeColors.onBackground};
            --primary-color: ${themeColors.primary};
            --primary-container: ${themeColors.primaryContainer};
            --on-primary-container: ${themeColors.onPrimaryContainer};
            --outline-color: ${themeColors.outline};
            --on-surface: ${themeColors.onSurface};
        }
        
        @font-face {
            font-family: 'Roboto Flex';
            font-style: normal;
            src: url('https://app.vibedict/fonts/roboto_flex.ttf');
        }
        @font-face {
            font-family: 'Roboto Flex';
            font-style: italic;
            src: url('https://app.vibedict/fonts/roboto_flex.ttf');
            font-variation-settings: 'slnt' -10;
        }
        
        html {
            zoom: $zoomPercent%;
            scroll-padding-top: 50px;
        }
        
        body {
            font-family: 'Roboto Flex', sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            line-height: 1.5;
        }
        
        .entry-section {
            margin-bottom: 16px;
        }
        
        .entry-section.section-collapsed {
            margin-bottom: 0;
        }
        
        .entry-header {
            position: sticky;
            position: -webkit-sticky;
            top: 0;
            z-index: 100;
            background-color: var(--bg-color);
            padding: 12px 16px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
            user-select: none;
            -webkit-user-select: none;
        }
        
        .entry-header:active {
            background-color: var(--surface-color);
        }
        
        .entry-title {
            font-weight: 700;
            font-size: 16px;
            color: var(--primary-color);
        }
        
        .expand-icon {
            transition: transform 0.3s ease;
            font-size: 20px;
            color: var(--text-color);
        }
        
        .expand-icon.expanded {
            transform: rotate(180deg);
        }
        
        .entry-pills {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            padding: 8px 16px;
            background-color: var(--bg-color);
        }
        
        .entry-pill {
            padding: 6px 12px;
            border-radius: 8px;
            font-size: 12px;
            cursor: pointer;
            border: 1px solid var(--outline-color);
            background-color: transparent;
            color: var(--on-surface);
        }
        
        .entry-pill.selected {
            background-color: var(--primary-container);
            color: var(--on-primary-container);
            border-color: var(--primary-container);
        }
        
        .entry-body {
            overflow: hidden;
            transition: max-height 0.3s ease, opacity 0.3s ease;
        }
        
        .entry-body.collapsed {
            max-height: 0;
            opacity: 0;
        }
        
        .entry-body.expanded {
            max-height: none;
            opacity: 1;
        }
        
        .entry-iframe {
            width: 100%;
            border: none;
            background-color: transparent;
            overflow: hidden;
            display: block;
        }
        
        .loading-spinner {
            display: flex;
            justify-content: center;
            padding: 20px;
        }
        
        @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
        }
        
        .spinner {
            width: 24px;
            height: 24px;
            border: 3px solid var(--divider-color);
            border-top-color: var(--primary-color);
            border-radius: 50%;
            animation: spin 1s linear infinite;
        }
    </style>
</head>
<body>
    $entriesHtml
    
    <script>
        // Store iframe selection for Define function
        window.iframeSelectedText = '';
        
        // Listen for selection messages from iframes
        window.addEventListener('message', function(event) {
            if (event.data && event.data.type === 'iframeSelection') {
                window.iframeSelectedText = event.data.text || '';
            }
        });
        
        // Override getSelection to include iframe text
        (function() {
            var originalGetSelection = window.getSelection.bind(window);
            window.getSelection = function() {
                var sel = originalGetSelection();
                // Extend the selection object with a custom toString that checks iframe selection
                var originalToString = sel.toString.bind(sel);
                sel.toString = function() {
                    var text = originalToString();
                    if (!text && window.iframeSelectedText) {
                        return window.iframeSelectedText;
                    }
                    return text;
                };
                return sel;
            };
        })();
        
        // Toggle expand/collapse
        function toggleEntry(entryId) {
            const body = document.getElementById('body-' + entryId);
            const icon = document.getElementById('icon-' + entryId);
            const pills = document.getElementById('pills-' + entryId);
            
            const section = body.closest('.entry-section');
            if (body.classList.contains('collapsed')) {
                body.classList.remove('collapsed');
                body.classList.add('expanded');
                icon.classList.add('expanded');
                if (pills) pills.style.display = 'flex';
                if (section) section.classList.remove('section-collapsed');
            } else {
                body.classList.remove('expanded');
                body.classList.add('collapsed');
                icon.classList.remove('expanded');
                if (pills) pills.style.display = 'none';
                if (section) section.classList.add('section-collapsed');
            }
        }
        
        // Select entry pill
        function selectEntry(entryId, index) {
            console.log('selectEntry triggered for entry: ' + entryId + ', index: ' + index);
            // URL encode the entryId to prevent Chromium from blocking it if it contains special characters
            var targetUrl = 'vibedict://selectEntry/' + encodeURIComponent(entryId) + '/' + index;
            console.log('Attempting to redirect to: ' + targetUrl);
            
            var redirectTimeout = setTimeout(function() {
                console.error('Redirect failed or stuck for URL: ' + targetUrl);
            }, 2000);
            
            window.addEventListener('pagehide', function() {
                clearTimeout(redirectTimeout);
                console.log('Successfully redirected to: ' + targetUrl);
            }, { once: true });
            
            // This will trigger a navigation to refresh with new selection
            window.location.href = targetUrl;
        }
        
        // Auto-resize iframes
        function resizeIframe(iframe) {
            try {
                // Use body.scrollHeight + computed margins to get accurate content height.
                // We AVOID offsetHeight or documentElement.scrollHeight as they can reflect 
                // the current iframe height, leading to a feedback loop (pumping).
                var doc = iframe.contentWindow.document;
                var body = doc.body;
                var style = iframe.contentWindow.getComputedStyle(body);
                var marginTop = parseInt(style.marginTop) || 0;
                var marginBottom = parseInt(style.marginBottom) || 0;
                
                var height = body.scrollHeight + marginTop + marginBottom;
                iframe.style.height = (height + 10) + 'px';
            } catch(e) {
                // Cross-origin - use fallback height
                iframe.style.height = '400px';
            }
        }
        
        // Observe iframe content for size changes
        document.querySelectorAll('.entry-iframe').forEach(function(iframe) {
            iframe.onload = function() {
                resizeIframe(iframe);
                
                // Set up ResizeObserver inside iframe
                try {
                    var observer = new ResizeObserver(function() {
                        resizeIframe(iframe);
                    });
                    observer.observe(iframe.contentWindow.document.body);
                } catch(e) {}
            };
        });
    </script>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Generate a single entry section with header and iframe content.
     */
    private fun generateEntrySection(
        entry: DictionaryEntry,
        content: String,
        customFontPaths: String,
        isExpanded: Boolean,
        isDarkTheme: Boolean,
        forceOriginalStyle: Boolean,
        entryIndex: Int,
        selectedIndex: Int,
        themeColors: ThemeColors
    ): String {
        val entryId = entry.id.hashCode().toString()
        val expandedClass = if (isExpanded) "expanded" else "collapsed"
        val sectionCollapsedClass = if (!isExpanded) "section-collapsed" else ""
        val iconExpandedClass = if (isExpanded) "expanded" else ""

        val pillsHtml = if (entry.entries.size > 1 && isExpanded) {
            val pills = entry.entries.mapIndexed { index, _ ->
                val selectedClass = if (index == selectedIndex) "selected" else ""
                """<span class="entry-pill $selectedClass" onclick="selectEntry('${entry.id}', $index)">Entry ${index + 1}</span>"""
            }.joinToString("\n")
            """<div id="pills-$entryId" class="entry-pills">$pills</div>"""
        } else ""

        val iframeContent = generateIframeContent(
            content = content,
            customCss = entry.customCss,
            customJs = entry.customJs,
            customFontPaths = customFontPaths,
            isDarkTheme = isDarkTheme,
            forceOriginalStyle = forceOriginalStyle,
            dictId = entry.id
        )

        val loadingHtml = if (entry.isLoading) {
            """<div class="loading-spinner"><div class="spinner"></div></div>"""
        } else ""

        // SVG expand_more icon matching Material Icons
        val expandIconSvg = """<svg id="icon-$entryId" class="expand-icon $iconExpandedClass" xmlns="http://www.w3.org/2000/svg" height="24" viewBox="0 -960 960 960" width="24" fill="currentColor"><path d="M480-345 240-585l56-56 184 184 184-184 56 56-240 240Z"/></svg>"""

        return """
        <div class="entry-section $sectionCollapsedClass" data-entry-id="${entry.id}">
            <div class="entry-header" onclick="toggleEntry('$entryId')">
                <span class="entry-title">${escapeHtml(entry.dictionaryName)}</span>
                $expandIconSvg
            </div>
            $pillsHtml
            <div id="body-$entryId" class="entry-body $expandedClass">
                $loadingHtml
                ${if (!entry.isLoading) {
                    IframeCache.cache[entry.id] = iframeContent
                    """<iframe class="entry-iframe" scrolling="no" sandbox="allow-scripts allow-same-origin" src="https://app.vibedict/entry_html?id=${android.net.Uri.encode(entry.id)}"></iframe>"""
                } else ""}
            </div>
        </div>
        """.trimIndent()
    }

    object IframeCache {
        val cache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }

    /**
     * Generate the content for an iframe with font isolation.
     */
    private fun generateIframeContent(
        content: String,
        customCss: String,
        customJs: String,
        customFontPaths: String,
        isDarkTheme: Boolean,
        forceOriginalStyle: Boolean,
        dictId: String
    ): String {
        val iframeOverflowCss = """"
    html, body {
        overflow: hidden !important;
        overflow-x: hidden !important;
        height: auto !important;
        min-height: 0 !important;
        max-height: none !important;
    }
    
    /* Override the clipping negative margin from style.css */
    ls[w], ls {
        margin-left: 0 !important;
    }
    
    /* Optional: Add a tiny bit of padding to the iframe body so text doesn't touch the absolute edge */
    body {
        padding: 0 4px !important;
    }
""".trimIndent()

        val transparencyCss = "html, body { background-color: transparent !important; }"

        val darkModeCss = if (isDarkTheme && !forceOriginalStyle) {
            """
            html { filter: invert(1) hue-rotate(180deg); }
            img, video, iframe, .handwriting_img, .wordsource_img { filter: invert(1) hue-rotate(180deg); }
            """.trimIndent()
        } else ""

        val fontCss = if (customFontPaths.isNotEmpty()) {
            val fontList = customFontPaths.split(",").filter { it.isNotBlank() }
            val fontFaceDeclarations = fontList.joinToString("\n") { path ->
                val fontFileName = path.substringAfterLast('/')
                val encodedFileName = URLEncoder.encode(fontFileName, "UTF-8").replace("+", "%20")
                val fontFamilyName = fontFileName.substringBeforeLast('.')
                """
                @font-face {
                    font-family: '$fontFamilyName';
                    src: url('https://app.vibedict/fonts/$encodedFileName');
                }
                """
            }
            val firstFontFamily = fontList.firstOrNull()?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
            "$fontFaceDeclarations\nbody { font-family: '$firstFontFamily', sans-serif !important; }"
        } else {
            """
            @font-face {
                font-family: 'Roboto Flex';
                font-style: normal;
                src: url('https://app.vibedict/fonts/roboto_flex.ttf');
            }
            @font-face {
                font-family: 'Roboto Flex';
                font-style: italic;
                src: url('https://app.vibedict/fonts/roboto_flex.ttf');
                font-variation-settings: 'slnt' -10;
            }
            body {
                font-family: 'Roboto Flex', sans-serif;
            }
            """
        }

        val sanitizedCss = customCss.replace("</?style[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")
        val sanitizedJs = customJs.replace("</?script[^>]*>".toRegex(RegexOption.IGNORE_CASE), "")

        val finalCss = "$iframeOverflowCss\n$sanitizedCss\n$transparencyCss\n$darkModeCss\n$fontCss"

        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>$finalCss</style>
</head>
<body>
    $content
    <script>$sanitizedJs</script>
    <script>
        // Notify parent of size changes
        new ResizeObserver(function() {
            parent.postMessage({type: 'resize', height: document.body.scrollHeight}, '*');
        }).observe(document.body);
        
        // Post selection changes to parent for Define function
        document.addEventListener('selectionchange', function() {
            var sel = window.getSelection();
            var text = sel ? sel.toString() : '';
            parent.postMessage({type: 'iframeSelection', text: text}, '*');
        });
        
        window.addEventListener('message', function(e) {
            if (e.data && e.data.type === 'scrollTo') {
                var hashId = e.data.hash;
                var el = document.getElementById(hashId) || document.getElementsByName(hashId)[0];
                if (!el) {
                    try { el = document.querySelector('[id="' + hashId.replace(/"/g, '\\"') + '"]'); } catch(err) {}
                }
                if (el) el.scrollIntoView();
            }
        });
        
        // Track link clicks for debugging redirection
        document.addEventListener('click', function(e) {
            var target = e.target.closest('a');
            
            // Add comprehensive logging out here to see what we caught
            if (target) {
                console.log('Iframe link clicked. target.href: ' + target.href);
                console.log('Iframe link clicked. target.getAttribute("href"): ' + target.getAttribute('href'));
                try {
                    console.log('Iframe link clicked. innerText: ' + target.innerText);
                } catch(e) {}
            }
            
            if (target && target.href) {
                var clickedUrl = target.href;
                console.log('Iframe link clicked, attempting to redirect to: ' + clickedUrl);
                
                // Ensure URLs with unescaped special characters (like 【】, (), spaces, or Japanese) 
                // are valid URIs, otherwise Chromium drops the navigation before Android sees it.
                if (clickedUrl.startsWith('entry://') || clickedUrl.startsWith('sound://') || clickedUrl.startsWith('content://')) {
                    e.preventDefault();
                    var finalUrl = clickedUrl;
                    
                    if (clickedUrl.startsWith('entry://')) {
                        var word = clickedUrl.substring(8);
                        try { word = decodeURIComponent(word); } catch(err) {}
                        
                        if (word.startsWith('#')) {
                            var hashId = word.substring(1);
                            var el = document.getElementById(hashId) || document.getElementsByName(hashId)[0];
                            if (!el) {
                                try { el = document.querySelector('[id="' + hashId.replace(/"/g, '\\"') + '"]'); } catch(e) {}
                            }
                            if (el) el.scrollIntoView();
                            return;
                        }
                        
                        finalUrl = 'entry://' + encodeURIComponent(word).replace(/['()~*!]/g, function(c) {
                            return '%' + c.charCodeAt(0).toString(16).toUpperCase();
                        });
                    } else if (clickedUrl.startsWith('sound://')) {
                        var word = clickedUrl.substring(8);
                        try { word = decodeURIComponent(word); } catch(err) {}
                        finalUrl = 'sound://' + encodeURIComponent(word).replace(/['()~*!]/g, function(c) {
                            return '%' + c.charCodeAt(0).toString(16).toUpperCase();
                        });
                    } else {
                        try {
                            finalUrl = encodeURI(decodeURI(clickedUrl));
                        } catch (err) {
                            finalUrl = encodeURI(clickedUrl);
                        }
                    }
                    
                    var redirectTimeout = setTimeout(function() {
                        console.error('Iframe redirect stuck or failed for URL: ' + finalUrl);
                    }, 2000);
                    
                    window.addEventListener('pagehide', function() {
                        clearTimeout(redirectTimeout);
                        console.log('Iframe successfully redirected for URL: ' + finalUrl);
                    }, { once: true });
                    
                    window.location.href = finalUrl;
                    return;
                }
                
                var redirectTimeout = setTimeout(function() {
                    console.error('Iframe redirect stuck or failed for URL: ' + clickedUrl);
                }, 2000);
                
                window.addEventListener('pagehide', function() {
                    clearTimeout(redirectTimeout);
                    console.log('Iframe successfully redirected for URL: ' + clickedUrl);
                }, { once: true });
            }
        });
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun escapeHtmlAttribute(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .replace("\n", "&#10;")
            .replace("\r", "&#13;")
    }
}