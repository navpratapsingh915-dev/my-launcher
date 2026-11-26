package com.example.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppEntry(
    val label: String,
    val pkg: String,
    val activity: String,
) {
    /** Section letter: A-Z, or '#' for everything else. */
    val letter: Char = label.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'
}

class IconData(val bitmap: ImageBitmap, val mono: Boolean)

object IconCache {
    private val cache = ConcurrentHashMap<String, IconData>()

    fun clear() = cache.clear()

    /** Returns the themed (monochrome) icon if requested and available (Android 13+), else the normal icon. */
    fun get(ctx: Context, pkg: String, wantMono: Boolean): IconData? {
        val key = "$pkg/$wantMono"
        cache[key]?.let { return it }
        val drawable = try {
            ctx.packageManager.getApplicationIcon(pkg)
        } catch (e: Exception) {
            return null
        }
        val mono = if (wantMono && Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) {
            drawable.monochrome
        } else null
        val data = if (mono != null) {
            IconData(mono.toBitmap(192, 192).asImageBitmap(), true)
        } else {
            IconData(drawable.toBitmap(144, 144).asImageBitmap(), false)
        }
        cache[key] = data
        return data
    }
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var favorites by mutableStateOf<List<String>>(emptyList())
        private set

    /** Incremented when the Home button is pressed while we are already open. */
    var homeTick by mutableIntStateOf(0)
        private set

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            IconCache.clear()
            refresh()
        }
    }

    init {
        favorites = prefs.getString("fav", "").orEmpty().split(",").filter { it.isNotBlank() }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    fun goHome() {
        homeTick++
    }

    fun refresh() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { loadApps(ctx) }
            apps = list
            // Drop favorites whose app was uninstalled
            val installed = list.map { it.pkg }.toSet()
            val cleaned = favorites.filter { it in installed }
            if (cleaned != favorites) saveFavorites(cleaned)
        }
    }

    fun toggleFavorite(pkg: String) {
        saveFavorites(if (pkg in favorites) favorites - pkg else favorites + pkg)
    }

    private fun saveFavorites(list: List<String>) {
        favorites = list
        prefs.edit().putString("fav", list.joinToString(",")).apply()
    }

    private fun loadApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    pkg = it.activityInfo.packageName,
                    activity = it.activityInfo.name,
                )
            }
            .distinctBy { it.pkg + "/" + it.activity }
            .sortedBy { it.label.lowercase() }
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(packageReceiver)
    }
}
