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
            // Data class to hold menu item info including Intent
            data class MenuItemInfo(
                val itemId: Int,
                val groupId: Int,
                val order: Int,
                val title: CharSequence,
                val intent: android.content.Intent?,
                val icon: android.graphics.drawable.Drawable?
            )
            
            // Collect existing items (excluding our Define item and self-referential Vibedict items)
            val existingItems = mutableListOf<MenuItemInfo>()
            val ownPackage = context.packageName // "com.waltermelon.vibedict"
            
            for (i in 0 until menu.size()) {
                val item = menu.getItem(i)
                // Skip our own Define item
                if (item.itemId == MENU_ITEM_DEFINE_ID) continue
                
                // Skip items that point to our own app (self-referential Vibedict)
                val itemIntent = item.intent
                if (itemIntent != null) {
                    val targetPackage = itemIntent.component?.packageName ?: itemIntent.`package`
                    if (targetPackage == ownPackage) continue
                }
                
                existingItems.add(MenuItemInfo(
                    itemId = item.itemId,
                    groupId = item.groupId,
                    order = item.order,
                    title = item.title ?: "",
                    intent = item.intent,
                    icon = item.icon
                ))
            }
            
            // Clear menu and rebuild with Define first
            menu.clear()
            
            // Add Define item first with order 0
            menu.add(Menu.NONE, MENU_ITEM_DEFINE_ID, 0, context.getString(com.waltermelon.vibedict.R.string.define))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            
            // Re-add other items with incrementing order starting from 1, preserving Intent
            existingItems.forEachIndexed { index, info ->
                val newItem = menu.add(info.groupId, info.itemId, index + 1, info.title)
                newItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                // Restore Intent so external services work
                info.intent?.let { newItem.intent = it }
                info.icon?.let { newItem.icon = it }
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
