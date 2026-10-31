package com.appistan.installer

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LocalAppState(val installed: String? = null, val verified: Boolean = false)

class InstallerViewModel(application: Application) : AndroidViewModel(application) {
    val app = application as AppIstan
    val rows = app.engine.dao.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val local = MutableStateFlow<Map<String, LocalAppState>>(emptyMap())
    val error = MutableStateFlow<String?>(null)
    var pendingStart: Set<String> = emptySet()
    init { refreshInstalled() }
    fun refreshInstalled() = viewModelScope.launch(Dispatchers.IO) {
        local.value = app.catalog.apps.associate { item ->
            val installed = app.engine.checks.installed(item.id)
            item.id to LocalAppState(installed?.versionName, app.engine.checks.trusted(item.id).isNotEmpty())
        }
    }
    fun toggle(id: String) { selected.update { if (id in it) it - id else it + id } }
    fun start(ids: Set<String>) = viewModelScope.launch(Dispatchers.IO) {
        try { app.engine.start(ids); selected.value = emptySet() } catch (failure: Exception) { error.value = failure.message }
    }
    fun cancel(id: String) = viewModelScope.launch(Dispatchers.IO) {
        try { app.engine.cancel(id) } catch (failure: Exception) { error.value = failure.message }
    }
    fun retry(id: String) = viewModelScope.launch(Dispatchers.IO) {
        try { app.engine.retry(id) } catch (failure: Exception) { error.value = failure.message }
    }
}

class MainActivity : ComponentActivity() {
    private val model: InstallerViewModel by viewModels()
    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val installPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls() && model.pendingStart.isNotEmpty()) launchBatch(model.pendingStart)
        model.pendingStart = emptySet()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                AppScreen(model, onStart = { ids ->
                    if (!packageManager.canRequestPackageInstalls()) {
                        model.pendingStart = ids
                        installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    } else launchBatch(ids)
                }, onPrompt = { intent -> runCatching { startActivity(intent) }.onFailure { model.error.value = "No se pudo abrir la confirmación de Android. Pulsa Reintentar." } })
            }
        }
    }
    private fun launchBatch(ids: Set<String>) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        model.start(ids)
    }
    override fun onResume() { super.onResume(); model.refreshInstalled() }
}

@Composable private fun AppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFA7F1BE), onPrimary = Color(0xFF103D30), secondaryContainer = Color(0xFF254C3C), background = Color(0xFF101712), surface = Color(0xFF101712), surfaceVariant = Color(0xFF28352B))
    else lightColorScheme(primary = Color(0xFF176B50), onPrimary = Color.White, primaryContainer = Color(0xFFD0EED9), secondaryContainer = Color(0xFFE0EADF), background = Color(0xFFF6F7F2), surface = Color(0xFFF6F7F2), surfaceVariant = Color(0xFFE7ECE3))
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AppScreen(model: InstallerViewModel, onStart: (Set<String>) -> Unit, onPrompt: (Intent) -> Unit) {
    val rows by model.rows.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val local by model.local.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("Todas") }
    var about by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val running = rows.any { !it.phase.terminal }
    val finished = rows.count { it.phase.terminal }
    val downloading = BatchPolicy.hasPendingDownloads(rows)
    val prepared = BatchPolicy.preparedCount(rows)
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); model.error.value = null } }
    LaunchedEffect(rows.map { Triple(it.id, it.phase, it.sessionId) }, lifecycleState) {
        if (lifecycleState != Lifecycle.State.RESUMED) return@LaunchedEffect
        rows.firstOrNull { it.phase == Phase.AWAITING_USER && model.app.engine.hasPrompt(it.sessionId) }?.let { row ->
            model.app.engine.takePrompt(row.sessionId)?.let(onPrompt)
        }
        model.app.scope.launch { model.app.engine.installNext() }
        model.refreshInstalled()
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Downloading, null, tint = MaterialTheme.colorScheme.primary)
            Text("AppIstan", fontWeight = FontWeight.Bold)
        } }, actions = { TextButton(onClick = { about = true }) { Text("v${BuildConfig.VERSION_NAME.substringBefore('-')}") } }) },
        bottomBar = { Column {
            if (tab == 0 && selected.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("${selected.size} ${if (selected.size == 1) "seleccionada" else "seleccionadas"}", fontWeight = FontWeight.SemiBold); Text("Instalar o actualizar", style = MaterialTheme.typography.bodySmall) }
                    Button(enabled = !running, onClick = { onStart(selected); tab = 1 }) { Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Comenzar") }
                }
            }
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Outlined.Apps, null) }, label = { Text("Aplicaciones") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { BadgedBox(badge = { if (running) Badge { Text("${rows.size - finished}") } }) { Icon(Icons.Outlined.SystemUpdateAlt, null) } }, label = { Text("Instalaciones") })
            }
        } }
    ) { insets ->
        if (tab == 0) {
            val visible = CatalogFilter.apply(model.app.catalog.apps, category, query)
            LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(bottom = 16.dp)) {
                item { Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Text("PREPARA TU TELÉFONO", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text("Tus apps.\nEn un solo lugar.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                    Text("Elige tus favoritas. AppIstan se encarga de las descargas y te guía para instalarlas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("${model.app.catalog.apps.size} aplicaciones") }, leadingIcon = { Icon(Icons.Outlined.Apps, null, Modifier.size(16.dp)) })
                        AssistChip(onClick = {}, label = { Text("Sin cuenta de Google") }, leadingIcon = { Icon(Icons.Outlined.PersonOutline, null, Modifier.size(16.dp)) })
                    }
                    OutlinedTextField(query, onValueChange = { query = it }, placeholder = { Text("Buscar aplicaciones") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                } }
                item { Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("Todas", "Comunes") + model.app.catalog.apps.map { it.category }.distinct()).forEach { value -> FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value) }) }
                } }
                item { Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${visible.size} ${if (visible.size == 1) "aplicación" else "aplicaciones"}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { val eligible = visible.filter { local[it.id]?.verified == true }.map { it.id }.toSet(); model.selected.update { if (eligible.all(it::contains)) it - eligible else it + eligible } }) { Text("Seleccionar verificadas") }
                } }
                items(visible, key = { it.id }) { app ->
                    val state = local[app.id]
                    CatalogRow(app, state, app.id in selected, !running && state?.verified == true) { model.toggle(app.id) }
                }
                if (visible.isEmpty()) item { EmptyState("Sin resultados", "Prueba con otro nombre o categoría.", Icons.Outlined.Search) }
                item { Text("Las versiones se consultan al comenzar. El catálogo muestra aplicaciones con una fuente verificada y compatibles con tu dispositivo.", Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                EmptyState("Tu primera selección", "Elige aplicaciones y pulsa Comenzar.\nPrimero revisaremos Google Play Services.", Icons.Outlined.DownloadForOffline)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(insets), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text(when { !running -> "Lote finalizado"; downloading -> "Descargando el lote"; else -> "Instalando el lote" }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (downloading) "Descargas: $prepared de ${rows.size} procesadas" else "$finished de ${rows.size} procesadas · ${rows.count { it.phase == Phase.SUCCESS }} instaladas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { (if (downloading) prepared else finished).toFloat() / rows.size }, modifier = Modifier.fillMaxWidth())
                    Text(if (downloading) "Primero se descargan y verifican todas las aplicaciones. Después confirmarás las instalaciones una por una." else "Confirma cada instalación para continuar con la siguiente. Play Services no bloquea las aplicaciones seleccionadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                items(rows, key = { it.id }) { row -> QueueCard(row, onCancel = { model.cancel(row.id) }, onRetry = { model.retry(row.id) }) }
            }
        }
    }
    if (about) AlertDialog(onDismissRequest = { about = false }, confirmButton = { TextButton(onClick = { about = false }) { Text("Cerrar") } }, title = { Text("AppIstan") }, text = {
        Text("Versión ${BuildConfig.VERSION_NAME}\nCompilación ${BuildConfig.VERSION_CODE}\nCommit ${BuildConfig.GIT_COMMIT}\n\nDescargas mediante Aptoide y fuentes oficiales. Cada instalación requiere una firma verificada y la aprobación que solicite Android.")
    })
}

@Composable private fun CatalogRow(app: CatalogApp, state: LocalAppState?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 20.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(app.id, app.name)
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when { state == null -> "Comprobando dispositivo…"; !state.verified -> "No disponible · firma pendiente"; state.installed != null -> "Instalada · ${state.installed}"; else -> app.category }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Checkbox(checked = selected, onCheckedChange = { onClick() }, enabled = enabled)
    }
}

@Composable private fun AppIcon(id: String, name: String) {
    val resource = AppIcons.resource(id)
    if (resource != null) Image(painterResource(resource), contentDescription = null, modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)))
    else Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable private fun QueueCard(row: QueueItem, onCancel: () -> Unit, onRetry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppIcon(row.id, row.name)
                Icon(when (row.phase) { Phase.SUCCESS -> Icons.Outlined.CheckCircle; Phase.FAILED -> Icons.Outlined.ErrorOutline; Phase.SKIPPED -> Icons.Outlined.Info; Phase.AWAITING_USER -> Icons.Outlined.TouchApp; else -> Icons.Outlined.Downloading }, null,
                    tint = if (row.phase == Phase.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (row.position == 0) Text("PASO 1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(row.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (row.phase == Phase.DOWNLOADING) {
                if (row.total > 0) LinearProgressIndicator(progress = { (row.downloaded.toFloat() / row.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("${row.downloaded / 1_000_000} MB${if (row.total > 0) " / ${row.total / 1_000_000} MB" else ""}", style = MaterialTheme.typography.labelSmall)
            }
            Row(Modifier.align(Alignment.End)) {
                if (row.phase in setOf(Phase.FAILED, Phase.CANCELLED, Phase.AWAITING_USER)) TextButton(onClick = onRetry) { Text("Reintentar") }
                if (!row.phase.terminal) TextButton(onClick = onCancel) { Text("Cancelar") }
            }
        }
    }
}

@Composable private fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}
