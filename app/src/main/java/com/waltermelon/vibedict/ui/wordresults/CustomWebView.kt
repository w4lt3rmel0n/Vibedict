package com.waltermelon.vibedict.ui.wordresults

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView

class CustomWebView(context: Context) : WebView(context) {

    var onDefineRequested: ((String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        return super.startActionMode(CustomActionModeCallback(callback), type)
    }

    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? {
        return super.startActionMode(CustomActionModeCallback(callback))
    }

    private inner class CustomActionModeCallback(private val wrapped: ActionMode.Callback?) : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val result = wrapped?.onCreateActionMode(mode, menu) ?: false
            menu?.let {
                addDefineItem(it)
            }
            return result
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val result = wrapped?.onPrepareActionMode(mode, menu) ?: false
            menu?.let {
                addDefineItem(it)
            }
            return result
        }

        private fun addDefineItem(menu: Menu) {
            // Add "Define" item if not present, at index 0 to be first
            if (menu.findItem(MENU_ITEM_DEFINE_ID) == null) {
                menu.add(Menu.NONE, MENU_ITEM_DEFINE_ID, 0, context.getString(com.waltermelon.vibedict.R.string.define))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            if (item?.itemId == MENU_ITEM_DEFINE_ID) {
                getSelectedText { text ->
                    if (text.isNotBlank()) {
                        onDefineRequested?.invoke(text)
                    }
                    // Finish mode AFTER retrieving text to avoid race condition
                    mode?.finish()
                }
                return true
            }
            return wrapped?.onActionItemClicked(mode, item) ?: false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            wrapped?.onDestroyActionMode(mode)
        }

        override fun onGetContentRect(mode: ActionMode?, view: android.view.View?, outRect: android.graphics.Rect?) {
            if (wrapped is ActionMode.Callback2) {
                wrapped.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }
    }

    private fun getSelectedText(callback: (String) -> Unit) {
        evaluateJavascript("(function(){return window.getSelection().toString()})()") { value ->
            // value is returned as a JSON string, e.g., "selected text"
            // We need to strip the quotes and handle escaped characters
            try {
                val text = if (value != null && value != "null") {
                    org.json.JSONTokener(value).nextValue().toString()
                } else {
                    ""
                }
                callback(text)
            } catch (e: Exception) {
                e.printStackTrace()
                callback("")
            }
        }
    }

    companion object {
        private const val MENU_ITEM_DEFINE_ID = 100001
    }
}
