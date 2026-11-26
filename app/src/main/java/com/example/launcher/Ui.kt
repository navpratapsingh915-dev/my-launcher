package com.example.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.exp
import kotlin.math.roundToInt

private val TextWhite = Color.White
private val SoftShadow = Shadow(color = Color(0x66000000), blurRadius = 6f)

private enum class Mode { Home, List }

private sealed interface ListItem {
    data class Header(val letter: Char) : ListItem
    data class App(val app: AppEntry) : ListItem
}

// ---------------------------------------------------------------------------------------------
// Root screen
// ---------------------------------------------------------------------------------------------

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var mode by remember { mutableStateOf(Mode.Home) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(vm.homeTick) {
        mode = Mode.Home
        searching = false
        query = ""
    }
    BackHandler(enabled = mode == Mode.List) {
        if (searching) {
            searching = false
            query = ""
        } else mode = Mode.Home
    }

    val sections = remember(vm.apps) {
        vm.apps.groupBy { it.letter }.toSortedMap(compareBy<Char> { it == '#' }.thenBy { it })
    }
    val letters = remember(sections) { sections.keys.toList() }
    val rows = remember(sections) {
        buildList<ListItem> {
            sections.forEach { (letter, list) ->
                add(ListItem.Header(letter))
                list.forEach { add(ListItem.App(it)) }
            }
        }
    }
    val headerIndex = remember(rows) {
        rows.withIndex()
            .filter { it.value is ListItem.Header }
            .associate { (it.value as ListItem.Header).letter to it.index }
    }
    val displayRows = if (searching && query.isNotBlank()) {
        remember(query, vm.apps) {
            vm.apps.filter { it.label.contains(query, ignoreCase = true) }.map { ListItem.App(it) }
        }
    } else rows

    val dim by animateFloatAsState(if (mode == Mode.List) 0.45f else 0.12f, label = "dim")

    val openApp: (AppEntry) -> Unit = { app ->
        launchApp(ctx, app)
        // Return to the home page for next time
        mode = Mode.Home
        searching = false
        query = ""
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = dim))
    ) {
        // ---------------- Home page: clock + favourites ----------------
        AnimatedVisibility(
            visible = mode == Mode.Home,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            HomePage(vm = vm, onLaunch = openApp)
        }

        // ---------------- All apps list ----------------
        AnimatedVisibility(
            visible = mode == Mode.List,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 28.dp, end = 76.dp, top = 96.dp, bottom = 140.dp
                ),
            ) {
                items(displayRows) { row ->
                    when (row) {
                        is ListItem.Header -> Text(
                            text = row.letter.toString(),
                            modifier = Modifier.padding(top = 18.dp, bottom = 2.dp),
                            style = TextStyle(
                                color = TextWhite.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        is ListItem.App -> AppRow(
                            app = row.app,
                            isFav = row.app.pkg in vm.favorites,
                            mono = false,
                            onLaunch = { openApp(row.app) },
                            onToggleFav = { vm.toggleFavorite(row.app.pkg) },
                            onInfo = { openAppInfo(ctx, row.app.pkg) },
                            onUninstall = { uninstall(ctx, row.app.pkg) },
                        )
                    }
                }
            }
        }

        // ---------------- A-Z rail ----------------
        val labels = remember(letters) { listOf("★") + letters.map { it.toString() } }
        AlphabetRail(
            labels = labels,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(bottom = 40.dp),
            onSelect = { idx ->
                if (idx == 0) {
                    mode = Mode.Home
                    searching = false
                    query = ""
                } else {
                    mode = Mode.List
                    val index = headerIndex[letters[idx - 1]]
                    if (index != null) scope.launch { listState.scrollToItem(index) }
                }
            },
        )

        // ---------------- Search ----------------
        if (mode == Mode.List) {
            if (!searching) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(end = 24.dp, bottom = 24.dp)
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                        .clickable { searching = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFF333333))
                }
            } else {
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { focus.requestFocus() }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(start = 20.dp, end = 76.dp, bottom = 16.dp)
                        .fillMaxWidth()
                        .focusRequester(focus),
                    singleLine = true,
                    placeholder = { Text("Search apps") },
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.92f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.92f),
                        focusedTextColor = Color(0xFF222222),
                        unfocusedTextColor = Color(0xFF222222),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        // Launch the first match, like pressing Enter in a launcher
                        (displayRows.firstOrNull { it is ListItem.App } as? ListItem.App)?.let { openApp(it.app) }
                    }),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Home page
// ---------------------------------------------------------------------------------------------

@Composable
private fun HomePage(vm: LauncherViewModel, onLaunch: (AppEntry) -> Unit) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    val timeText = now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", Locale.getDefault()))
    val dateText = now.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()))

    val favApps = remember(vm.apps, vm.favorites) {
        vm.favorites.mapNotNull { pkg -> vm.apps.firstOrNull { it.pkg == pkg } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 28.dp, end = 76.dp)
    ) {
        Spacer(Modifier.weight(0.16f))
        Text(
            timeText,
            style = TextStyle(
                color = TextWhite, fontSize = 44.sp, fontWeight = FontWeight.Light, shadow = SoftShadow
            ),
        )
        Text(
            dateText,
            style = TextStyle(color = TextWhite.copy(alpha = 0.85f), fontSize = 15.sp, shadow = SoftShadow),
        )
        Spacer(Modifier.height(28.dp))

        if (favApps.isEmpty()) {
            Text(
                "Open the list (A-Z), long-press an app and choose \"Add to favourites\".",
                style = TextStyle(color = TextWhite.copy(alpha = 0.7f), fontSize = 15.sp, shadow = SoftShadow),
            )
        } else {
            favApps.forEach { app ->
                AppRow(
                    app = app,
                    isFav = true,
                    mono = true,
                    onLaunch = { onLaunch(app) },
                    onToggleFav = { vm.toggleFavorite(app.pkg) },
                    onInfo = { openAppInfo(ctx, app.pkg) },
                    onUninstall = { uninstall(ctx, app.pkg) },
                )
            }
        }
        Spacer(Modifier.weight(0.84f))
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> {
    val ctx = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(ctx) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                now.value = LocalDateTime.now()
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now.value = LocalDateTime.now()
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return now
}

// ---------------------------------------------------------------------------------------------
// One app row (icon + label) with long-press menu
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: AppEntry,
    isFav: Boolean,
    mono: Boolean,
    onLaunch: () -> Unit,
    onToggleFav: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onLaunch, onLongClick = { menu = true })
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.pkg, 30.dp, mono)
            Spacer(Modifier.width(18.dp))
            Text(
                app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Medium, shadow = SoftShadow
                ),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(if (isFav) "Remove from favourites" else "Add to favourites") },
                onClick = { menu = false; onToggleFav() },
            )
            DropdownMenuItem(text = { Text("App info") }, onClick = { menu = false; onInfo() })
            DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menu = false; onUninstall() })
        }
    }
}

@Composable
private fun AppIcon(pkg: String, size: Dp, mono: Boolean) {
    val ctx = LocalContext.current
    val icon by produceState<IconData?>(null, pkg, mono) {
        value = withContext(Dispatchers.IO) { IconCache.get(ctx, pkg, mono) }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        icon?.let {
            Image(
                bitmap = it.bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    // Monochrome layers live in a 108dp canvas with a 72dp safe zone
                    .then(if (it.mono) Modifier.scale(1.5f) else Modifier),
                colorFilter = if (it.mono) ColorFilter.tint(TextWhite) else null,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// A-Z rail: letters bend towards the finger, a bubble follows it
// ---------------------------------------------------------------------------------------------

@Composable
private fun AlphabetRail(
    labels: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemH = 22.dp
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val itemHPx = with(density) { itemH.toPx() }
    val maxShiftPx = with(density) { 84.dp.toPx() }
    val bubbleSize = 52.dp
    val bubbleSizePx = with(density) { bubbleSize.toPx() }
    val sigma = itemHPx * 3.2f

    var touchY by remember { mutableFloatStateOf(0f) }
    var active by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(0) }
    val presence by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "railPresence",
    )
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(modifier.width(180.dp).height(itemH * labels.size)) {
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(48.dp)
                .height(itemH * labels.size)
                .pointerInput(labels) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var last = -1
                        fun update(y: Float) {
                            touchY = y
                            val idx = (y / itemHPx).toInt().coerceIn(0, labels.lastIndex)
                            if (idx != last) {
                                last = idx
                                selected = idx
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                currentOnSelect(idx)
                            }
                        }
                        active = true
                        update(down.position.y)
                        down.consume()
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.first()
                            update(change.position.y)
                            change.consume()
                        } while (event.changes.any { it.pressed })
                        active = false
                    }
                },
        ) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .height(itemH)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val cy = (i + 0.5f) * itemHPx
                            val d = (cy - touchY) / sigma
                            translationX = -presence * maxShiftPx * exp(-d * d)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = TextStyle(
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            shadow = SoftShadow,
                        ),
                    )
                }
            }
        }

        if (presence > 0.01f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = -(maxShiftPx + with(density) { 40.dp.toPx() }).roundToInt(),
                            y = (touchY - bubbleSizePx / 2f).roundToInt(),
                        )
                    }
                    .size(bubbleSize)
                    .graphicsLayer { alpha = presence; scaleX = 0.6f + 0.4f * presence; scaleY = 0.6f + 0.4f * presence }
                    .background(Color.White.copy(alpha = 0.92f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    labels.getOrElse(selected) { "" },
                    style = TextStyle(color = Color(0xFF222222), fontSize = 22.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------------------------

private fun launchApp(ctx: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
        setClassName(app.pkg, app.activity)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
    }
}

private fun openAppInfo(ctx: Context, pkg: String) {
    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(i)
    } catch (_: Exception) {
    }
}

private fun uninstall(ctx: Context, pkg: String) {
    val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(i)
    } catch (_: Exception) {
    }
}
