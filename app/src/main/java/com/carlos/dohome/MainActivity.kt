package com.carlos.dohome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

private val Amber = Color(0xFFFFB938)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Color(0xFF2B1D00),
    primaryContainer = Color(0xFF4A3500),
    onPrimaryContainer = Color(0xFFFFDEA6),
    secondaryContainer = Color(0xFF3B3346),
    onSecondaryContainer = Color(0xFFEDE3F7),
    background = Color(0xFF121016),
    onBackground = Color(0xFFEDE9F2),
    surface = Color(0xFF121016),
    onSurface = Color(0xFFEDE9F2),
    surfaceVariant = Color(0xFF2A2631),
    onSurfaceVariant = Color(0xFFCBC4D4),
    surfaceContainer = Color(0xFF1D1A22),
    surfaceContainerHigh = Color(0xFF27232D),
    surfaceContainerHighest = Color(0xFF312C38),
    outline = Color(0xFF8F889A),
    outlineVariant = Color(0xFF48434F),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF805600),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDA6),
    onPrimaryContainer = Color(0xFF291800),
    secondaryContainer = Color(0xFFEDE1F5),
    onSecondaryContainer = Color(0xFF241A2E),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1F1B16),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFFF0E0CF),
    onSurfaceVariant = Color(0xFF4F4539),
    surfaceContainer = Color(0xFFF7ECE1),
    surfaceContainerHigh = Color(0xFFF1E6DB),
    surfaceContainerHighest = Color(0xFFEBE0D5),
    outline = Color(0xFF817567),
    outlineVariant = Color(0xFFD3C4B4),
)

class MainActivity : ComponentActivity() {
    private val vm: LightViewModel by viewModels()

    /** Ao voltar para o app, relê a lâmpada (o widget pode ter mudado algo). */
    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                // Surface com cor do tema: define o contentColor certo para textos e ícones
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(vm) }
            }
        }
    }
}

// Curvas de motion (nomes da biblioteca apenasdouglas/ai-motion-library, equivalentes do GSAP)
private val EaseOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)      // power2.out
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)    // power2.inOut
private val SineInOut = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)    // sine.inOut
private val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)   // back.out(1.7)

/** Cascata (stagger) + surgir/deslizar: cada item entra 80 ms depois do anterior. */
@Composable
private fun Modifier.enter(index: Int): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 80L)
        p.animateTo(1f, tween(450, easing = EaseOut))
    }
    return graphicsLayer {
        alpha = p.value
        translationY = (1f - p.value) * 28.dp.toPx()
    }
}

/** Número contando (count-up) com algarismos de largura fixa. */
@Composable
private fun CountText(value: Int, suffix: String, style: TextStyle, color: Color) {
    val shown by animateIntAsState(value, tween(450, easing = EaseOut), label = "count")
    Text("$shown$suffix", style = style.copy(fontFeatureSettings = "tnum"), color = color)
}

private val EFFECTS = listOf(
    1 to "Arco-íris suave", 22 to "Arco-íris pulando", 23 to "Arco-íris piscando",
    25 to "RGB suave", 26 to "RGB pulando", 27 to "RGB piscando",
    2 to "Vermelho pulsando", 3 to "Verde pulsando", 4 to "Azul pulsando",
    5 to "Amarelo pulsando", 6 to "Ciano pulsando", 7 to "Roxo pulsando", 8 to "Branco pulsando",
    9 to "Vermelho estrobo", 10 to "Verde estrobo", 11 to "Azul estrobo", 12 to "Amarelo estrobo",
    24 to "Branco estrobo",
)

private val PRESETS = listOf(
    0f to 1f, 25f to 1f, 50f to 1f, 120f to 1f, 180f to 1f, 220f to 1f, 270f to 1f, 310f to 1f, 30f to 0.35f,
)

@Composable
fun App(vm: LightViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var showWifi by remember { mutableStateOf(false) }
    var showConnection by remember { mutableStateOf(ui.ip.isBlank()) }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.enter(0)) {
            TopBar(ui, onWifi = { showWifi = true }, onRefresh = vm::refresh, onConnection = { showConnection = !showConnection })
        }

        AnimatedVisibility(
            showConnection || ui.ip.isBlank(),
            enter = fadeIn(tween(300, easing = EaseOut)) + expandVertically(tween(350, easing = EaseOut)),
            exit = fadeOut(tween(200)) + shrinkVertically(tween(300, easing = EaseInOut)),
        ) { ConnectionCard(ui, vm) }

        AnimatedVisibility(ui.status.isNotBlank() && (!ui.connected || ui.busy), enter = fadeIn(), exit = fadeOut()) {
            Text(ui.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (ui.ip.isNotBlank()) {
            Box(Modifier.enter(1)) { PowerHero(ui, vm) }
            if (ui.isRgb) Box(Modifier.enter(2)) { ModeTabs(ui, vm) }
            Box(Modifier.enter(3)) {
                when (ui.mode) {
                    Mode.COLOR -> ColorCard(ui, vm)
                    Mode.WHITE -> WhiteCard(ui, vm)
                    Mode.EFFECT -> EffectsCard(ui, vm)
                }
            }
            if (ui.mode != Mode.EFFECT) Box(Modifier.enter(4)) { BrightnessCard(ui, vm) }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showWifi) WifiDialog(ui, vm) { showWifi = false }
}

@Composable
private fun TopBar(ui: UiState, onWifi: () -> Unit, onRefresh: () -> Unit, onConnection: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("DoHome", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onConnection)) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (ui.connected) Color(0xFF4CC46B) else MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        ui.ip.isBlank() -> "Nenhuma lâmpada"
                        ui.connected -> listOf(ui.model.ifBlank { null }, ui.ip).filterNotNull().joinToString(" · ")
                        else -> "Desconectada · ${ui.ip}"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (ui.busy) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
        FilledTonalIconButton(onClick = onConnection) { Icon(Icons.Default.Router, "Conexão") }
        FilledTonalIconButton(onClick = onWifi) { Icon(Icons.Default.Wifi, "Configurar Wi-Fi") }
        FilledTonalIconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Atualizar") }
    }
}

@Composable
private fun SectionCard(
    title: String, icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun ConnectionCard(ui: UiState, vm: LightViewModel) {
    var manualIp by remember(ui.ip) { mutableStateOf(ui.ip) }
    SectionCard("Conexão", Icons.Default.Router) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = manualIp, onValueChange = { manualIp = it },
                label = { Text("IP da lâmpada") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Button(onClick = { vm.selectIp(manualIp) }, enabled = manualIp.isNotBlank()) { Text("Usar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { vm.discover() }, enabled = !ui.busy) {
                Icon(Icons.Default.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Procurar")
            }
            OutlinedButton(onClick = { vm.selectIp(DoHome.AP_IP) }) { Text("Modo direto") }
        }
        Text(
            "Modo direto: conecte o celular ao Wi-Fi DoHome_xxxx para controlar sem roteador.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ui.found.forEach { d ->
            val selected = d.ip == ui.ip
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                    .clickable { vm.selectIp(d.ip) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.name, fontWeight = FontWeight.SemiBold)
                    Text("${d.ip}  ${d.type.trim('_')}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (selected) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PowerHero(ui: UiState, vm: LightViewModel) {
    val lightColor = when (ui.mode) {
        Mode.WHITE -> kelvinColor(ui.kelvin)
        Mode.EFFECT -> Color(0xFFB57BFF)
        Mode.COLOR -> Color.hsv(ui.hue, ui.saturation, 1f)
    }
    val level = 0.35f + 0.65f * ui.brightness / 255f
    // Acelera e freia (ease in-out) nas trocas de cor
    val glow by animateColorAsState(if (ui.isOn) lightColor.copy(alpha = 0.6f * level) else Color.Transparent,
        tween(500, easing = EaseInOut), label = "glow")
    val bulb by animateColorAsState(if (ui.isOn) lightColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        tween(400, easing = EaseInOut), label = "bulb")
    val iconTint by animateColorAsState(if (ui.isOn) Color(0xFF231A00) else MaterialTheme.colorScheme.onSurfaceVariant, label = "tint")

    // Pulsar: a aura "respira" (1 → 1,08 → 1) enquanto a luz está ligada
    val breathe by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(1600, easing = SineInOut), RepeatMode.Reverse), label = "breathe")
    // Mola: o botão afunda ao pressionar e volta com elasticidade
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "press")
    // Aparecer com salto: o ícone dá um "pop" a cada liga/desliga
    val pop = remember { Animatable(1f) }
    LaunchedEffect(ui.isOn) {
        pop.snapTo(0.55f)
        pop.animateTo(1f, tween(350, easing = BackOut))
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize()
                    .graphicsLayer { val s = if (ui.isOn) breathe else 1f; scaleX = s; scaleY = s }
                    .background(Brush.radialGradient(listOf(glow, Color.Transparent)))
            )
            Box(
                Modifier.size(136.dp)
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .shadow(if (ui.isOn) 18.dp else 2.dp, CircleShape, ambientColor = lightColor, spotColor = lightColor)
                    .clip(CircleShape).background(bulb)
                    .border(2.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (ui.isOn) 0f else 1f), CircleShape)
                    .clickable(interactionSource = interaction, indication = null) { vm.setPower(!ui.isOn) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew, if (ui.isOn) "Desligar" else "Ligar", tint = iconTint,
                    modifier = Modifier.size(56.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
                )
            }
        }
        Text(if (ui.isOn) "Ligada" else "Desligada", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            if (ui.isOn) when (ui.mode) {
                Mode.WHITE -> "${ui.brightness * 100 / 255}% · ${kelvinName(ui.kelvin)}"
                Mode.COLOR -> "${ui.brightness * 100 / 255}% · cor"
                Mode.EFFECT -> EFFECTS.firstOrNull { it.first == ui.effect }?.second ?: "Efeito"
            } else "Toque no botão para ligar",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeTabs(ui: UiState, vm: LightViewModel) {
    val tabs = listOf(Triple(Mode.WHITE, "Branco", Icons.Default.WbSunny), Triple(Mode.COLOR, "Cor", Icons.Default.Palette),
        Triple(Mode.EFFECT, "Efeitos", Icons.Default.AutoAwesome))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { i, (m, label, icon) ->
            SegmentedButton(
                selected = ui.mode == m, onClick = { vm.setMode(m) },
                shape = SegmentedButtonDefaults.itemShape(i, tabs.size),
                icon = { Icon(icon, null, Modifier.size(18.dp)) },
            ) { Text(label) }
        }
    }
}

@Composable
private fun BrightnessCard(ui: UiState, vm: LightViewModel) {
    val pct = ui.brightness * 100 / 255
    SectionCard("Brilho", Icons.Default.BrightnessMedium, trailing = {
        CountText(pct, "%", MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.primary)
    }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.BrightnessLow, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            GradientSlider(ui.brightness.toFloat(), 1f..255f,
                Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHighest, Amber)),
                Modifier.weight(1f).padding(horizontal = 8.dp)) { vm.setBrightness(it.toInt()) }
            Icon(Icons.Default.BrightnessHigh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        ChoiceRow(listOf(10, 25, 50, 75, 100).map { p -> (p * 255 / 100).coerceAtLeast(1) to "$p%" }, ui.brightness) {
            vm.setBrightness(it)
        }
    }
}

@Composable
private fun WhiteCard(ui: UiState, vm: LightViewModel) {
    SectionCard("Temperatura", Icons.Default.Thermostat, trailing = {
        CountText(ui.kelvin, " K", MaterialTheme.typography.titleMedium, MaterialTheme.colorScheme.primary)
    }) {
        GradientSlider(ui.kelvin.toFloat(), DoHome.KELVIN_MIN.toFloat()..DoHome.KELVIN_MAX.toFloat(),
            Brush.horizontalGradient(listOf(kelvinColor(3000), kelvinColor(4700), kelvinColor(6400)))) { vm.setKelvin(it.toInt()) }
        ChoiceRow(listOf(3000 to "Quente", 4700 to "Neutro", 6400 to "Frio"), ui.kelvin,
            icons = listOf(Icons.Default.LocalFireDepartment, Icons.Default.WbSunny, Icons.Default.AcUnit)) { vm.setKelvin(it) }
    }
}

/** Linha de atalhos com larguras iguais (nada quebra linha, nada desalinha). */
@Composable
private fun ChoiceRow(
    options: List<Pair<Int, String>>, selected: Int,
    icons: List<ImageVector>? = null, onSelect: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, (value, label) ->
            val isSel = value == selected
            val bg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(250, easing = EaseInOut), label = "chipBg")
            val fg by animateColorAsState(
                if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, label = "chipFg")
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if (pressed) 0.92f else 1f,
                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium), label = "chipPress")
            Row(
                Modifier.weight(1f).height(40.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(12.dp)).background(bg)
                    .border(1.dp, if (isSel) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable(interactionSource = interaction, indication = null) { onSelect(value) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                icons?.getOrNull(i)?.let {
                    Icon(it, null, tint = if (isSel) fg else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ColorCard(ui: UiState, vm: LightViewModel) {
    SectionCard("Cor", Icons.Default.Palette) {
        GradientSlider(ui.hue, 0f..359f, Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })) {
            vm.setColor(it, ui.saturation)
        }
        Text("Saturação", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GradientSlider(ui.saturation, 0f..1f, Brush.horizontalGradient(listOf(Color.White, Color.hsv(ui.hue, 1f, 1f)))) {
            vm.setColor(ui.hue, it)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PRESETS.forEach { (h, s) ->
                val selected = ui.hue == h && ui.saturation == s
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(Color.hsv(h, s, 1f))
                        .border(if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { vm.setColor(h, s) }
                )
            }
        }
    }
}

@Composable
private fun EffectsCard(ui: UiState, vm: LightViewModel) {
    SectionCard("Efeitos", Icons.Default.AutoAwesome) {
        EFFECTS.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (idx, name) ->
                    FilterChip(selected = ui.effect == idx, onClick = { vm.setEffect(idx) },
                        label = { Text(name) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Slider com trilho em gradiente desenhado pelo próprio Slider: o trilho vai exatamente
 * do centro do pegador na ponta esquerda ao centro na ponta direita, então tudo alinha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientSlider(
    value: Float, range: ClosedFloatingPointRange<Float>, brush: Brush,
    modifier: Modifier = Modifier, onChange: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val dragged by interaction.collectIsDraggedAsState()
    val pressed by interaction.collectIsPressedAsState()
    // Mola: o pegador cresce enquanto arrasta
    val thumbScale by animateFloatAsState(if (dragged || pressed) 1.2f else 1f,
        spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "thumb")
    val ring = MaterialTheme.colorScheme.primary
    Slider(
        value = value, onValueChange = onChange, valueRange = range, modifier = modifier,
        interactionSource = interaction,
        thumb = {
            Box(
                Modifier.size(26.dp).graphicsLayer { scaleX = thumbScale; scaleY = thumbScale }
                    .shadow(4.dp, CircleShape).background(Color.White, CircleShape).border(3.dp, ring, CircleShape)
            )
        },
        track = {
            Box(
                Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(brush)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(7.dp))
            )
        },
    )
}

private fun kelvinColor(k: Int): Color {
    val t = (k - 3000) / 3400f
    return Color(red = 1f - 0.12f * t, green = 0.74f + 0.2f * t, blue = 0.40f + 0.6f * t)
}

private fun kelvinName(k: Int) = when {
    k < 3800 -> "quente"
    k < 5400 -> "neutro"
    else -> "frio"
}

@Composable
private fun WifiDialog(ui: UiState, vm: LightViewModel, onClose: () -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { Icon(Icons.Default.Wifi, null) },
        title = { Text("Colocar a lâmpada no seu Wi-Fi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1. Conecte o celular ao Wi-Fi DoHome_xxxx (se avisar \"sem internet\", mantenha conectado).\n" +
                        "2. Digite o nome e a senha do Wi-Fi da sua casa (precisa ser 2,4 GHz).\n" +
                        "3. Toque em Enviar e aguarde.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(ssid, { ssid = it }, label = { Text("Nome da rede (SSID)") }, singleLine = true)
                OutlinedTextField(
                    pass, { pass = it }, label = { Text("Senha") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (ui.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(ui.status, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { vm.provision(ssid.trim(), pass) { ok -> if (ok) onClose() } },
                enabled = ssid.isNotBlank() && !ui.busy) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } },
    )
}
