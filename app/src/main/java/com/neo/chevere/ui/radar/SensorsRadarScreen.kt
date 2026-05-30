package com.neo.chevere.ui.radar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neo.chevere.ui.designsystem.Typography
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorsRadarScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SensorsRadarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceContainer = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)

    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onMicPermissionGranted()
        }
    }

    LaunchedEffect(uiState.mode) {
        if (uiState.mode == SensorMode.SOUND &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onMicPermissionGranted()
        }
    }

    val screenTitle = when (uiState.mode) {
        SensorMode.ALL      -> "[ SENSOR RADAR ]"
        SensorMode.STUD     -> "[ STUD FINDER ]"
        SensorMode.LEVEL    -> "[ SPIRIT LEVEL ]"
        SensorMode.LIGHT    -> "[ AMBIENT LIGHT ]"
        SensorMode.PROXIMITY -> "[ PROXIMITY ]"
        SensorMode.SOUND    -> "[ DECIBEL METER ]"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = screenTitle,
                        style = Typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = primaryColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryColor
                        )
                    }
                },
                actions = {
                    if (uiState.mode == SensorMode.ALL || uiState.mode == SensorMode.STUD) {
                        IconButton(onClick = { viewModel.calibrate() }) {
                            Icon(
                                imageVector = Icons.Default.LocationSearching,
                                contentDescription = "Calibrate",
                                tint = primaryColor
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Status Banner (all modes) ──────────────────────────────────
            StatusBanner(
                label = uiState.statusLabel,
                alertColor = (uiState.mode == SensorMode.STUD && uiState.magneticCalibrated > 35f) ||
                        (uiState.mode == SensorMode.PROXIMITY && uiState.proximityNear) ||
                        (uiState.mode == SensorMode.SOUND && uiState.soundDbSpl > 75f),
                primaryColor = primaryColor,
                surfaceContainer = surfaceContainer
            )

            // ── Mode-specific content ──────────────────────────────────────
            when (uiState.mode) {
                SensorMode.ALL -> AllSensorsContent(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    surfaceContainer = surfaceContainer,
                    viewModel = viewModel
                )
                SensorMode.STUD -> StudFinderContent(
                    uiState = uiState,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    surfaceContainer = surfaceContainer,
                    viewModel = viewModel
                )
                SensorMode.LEVEL -> SpiritLevelContent(
                    pitch = uiState.pitch,
                    roll = uiState.roll,
                    primaryColor = primaryColor,
                    surfaceContainer = surfaceContainer
                )
                SensorMode.LIGHT -> LightSensorContent(
                    lightLevel = uiState.lightLevel,
                    primaryColor = primaryColor,
                    surfaceContainer = surfaceContainer
                )
                SensorMode.PROXIMITY -> ProximitySensorContent(
                    near = uiState.proximityNear,
                    distance = uiState.proximityDistance,
                    primaryColor = primaryColor,
                    surfaceContainer = surfaceContainer
                )
                SensorMode.SOUND -> {
                    if (!uiState.micPermissionGranted) {
                        MicPermissionPromptCard(
                            onRequestPermission = {
                                micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            primaryColor = primaryColor,
                            surfaceContainer = surfaceContainer
                        )
                    } else {
                        SoundSensorContent(
                            dbSpl = uiState.soundDbSpl,
                            history = uiState.soundHistory,
                            primaryColor = primaryColor,
                            surfaceContainer = surfaceContainer
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusBanner(
    label: String,
    alertColor: Boolean,
    primaryColor: Color,
    surfaceContainer: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        border = BoxBorder(primaryColor.copy(alpha = 0.4f))
    ) {
        Box(Modifier.padding(12.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = label.uppercase(),
                style = Typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace
                ),
                color = if (alertColor) MaterialTheme.colorScheme.error else primaryColor
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ALL mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AllSensorsContent(
    uiState: RadarUiState,
    primaryColor: Color,
    secondaryColor: Color,
    surfaceContainer: Color,
    viewModel: SensorsRadarViewModel
) {
    StudFinderContent(uiState, primaryColor, secondaryColor, surfaceContainer, viewModel)

    // Light & Proximity row
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = surfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("AMBIENT LIGHT", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uiState.lightLevel >= 0f) "${uiState.lightLevel.toInt()} lx" else "N/A",
                    style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = primaryColor
                )
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = surfaceContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("PROXIMITY", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (uiState.proximityNear) "NEAR" else "FAR",
                    style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                    color = if (uiState.proximityNear) MaterialTheme.colorScheme.error else primaryColor
                )
            }
        }
    }

    // Spirit Level
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("SPIRIT LEVEL", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            SpiritLevelVisualizer(pitch = uiState.pitch, roll = uiState.roll, primaryColor = primaryColor)
            Text(
                text = if (abs(uiState.pitch) < 2.0f && abs(uiState.roll) < 2.0f) "LEVEL" else "TILTED",
                style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = if (abs(uiState.pitch) < 2.0f && abs(uiState.roll) < 2.0f) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STUD mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StudFinderContent(
    uiState: RadarUiState,
    primaryColor: Color,
    secondaryColor: Color,
    surfaceContainer: Color,
    viewModel: SensorsRadarViewModel
) {
    RadarHUDVisualizer(
        magneticCalibrated = uiState.magneticCalibrated,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor
    )

    // Readings card
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("MAGNETIC READING", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f μT", uiState.magneticMagnitude),
                        style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("CALIBRATED (Δ)", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryColor)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f μT", uiState.magneticCalibrated),
                        style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = primaryColor
                    )
                }
            }

            HorizontalDivider(color = primaryColor.copy(alpha = 0.2f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    String.format(java.util.Locale.US, "Baseline: %.1f μT", uiState.baseline),
                    style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.calibrate() }) {
                        Text("TARE / ZERO OUT", color = primaryColor, style = Typography.labelLarge)
                    }
                    if (uiState.baseline > 0f) {
                        IconButton(onClick = { viewModel.resetCalibration() }) {
                            Icon(Icons.Default.Refresh, "Reset Calibration",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    // Sound & vibration toggles
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { viewModel.toggleAudio(!uiState.audioEnabled) }.padding(8.dp)
            ) {
                Icon(
                    imageVector = if (uiState.audioEnabled) Icons.Default.Hearing else Icons.Default.HearingDisabled,
                    contentDescription = "Audio Alert",
                    tint = if (uiState.audioEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("SOUND", style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (uiState.audioEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { viewModel.toggleVibration(!uiState.vibrationEnabled) }.padding(8.dp)
            ) {
                Icon(Icons.Default.Vibration, "Haptic Alert",
                    tint = if (uiState.vibrationEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("HAPTICS", style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (uiState.vibrationEnabled) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    // History graph
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SIGNAL HISTORY", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            HistoryGraph(history = uiState.magneticHistory, primaryColor = primaryColor)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LEVEL mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SpiritLevelContent(
    pitch: Float,
    roll: Float,
    primaryColor: Color,
    surfaceContainer: Color
) {
    val isLevel = abs(pitch) < 2.0f && abs(roll) < 2.0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SpiritLevelVisualizer(pitch = pitch, roll = roll, primaryColor = primaryColor, size = 160.dp)
            Text(
                text = if (isLevel) "● LEVEL" else "● TILTED",
                style = Typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace),
                color = if (isLevel) primaryColor else MaterialTheme.colorScheme.error
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("PITCH", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f°", pitch),
                        style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = if (abs(pitch) < 2f) primaryColor else MaterialTheme.colorScheme.error
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ROLL", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f°", roll),
                        style = Typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                        color = if (abs(roll) < 2f) primaryColor else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIGHT mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LightSensorContent(
    lightLevel: Float,
    primaryColor: Color,
    surfaceContainer: Color
) {
    val category = when {
        lightLevel < 0     -> "READING..."
        lightLevel < 10f   -> "DARK"
        lightLevel < 200f  -> "DIM"
        lightLevel < 1000f -> "NORMAL"
        lightLevel < 5000f -> "BRIGHT"
        else               -> "VERY BRIGHT"
    }
    val brightness = if (lightLevel >= 0f) (lightLevel / 10000f).coerceIn(0f, 1f) else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Default.WbSunny, contentDescription = null,
                tint = primaryColor.copy(alpha = 0.3f + brightness * 0.7f),
                modifier = Modifier.size(80.dp))
            Text(
                text = if (lightLevel >= 0f) "${lightLevel.toInt()} lx" else "—",
                style = Typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace),
                color = primaryColor
            )
            Text(category, style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(
                progress = { brightness },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = primaryColor,
                trackColor = primaryColor.copy(alpha = 0.15f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROXIMITY mode
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProximitySensorContent(
    near: Boolean,
    distance: Float,
    primaryColor: Color,
    surfaceContainer: Color
) {
    val pulseScale by animateFloatAsState(
        targetValue = if (near) 1.18f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ProximityPulse"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = if (near) Color.Red.copy(alpha = 0.15f * pulseScale) else primaryColor.copy(alpha = 0.1f),
                        radius = size.minDimension / 2 * pulseScale
                    )
                    drawCircle(
                        color = if (near) Color.Red else primaryColor,
                        radius = size.minDimension / 3,
                        style = Stroke(width = 4f)
                    )
                }
                Icon(Icons.Default.BlurOn, contentDescription = null,
                    tint = if (near) MaterialTheme.colorScheme.error else primaryColor,
                    modifier = Modifier.size(48.dp))
            }
            Text(
                text = if (near) "NEAR" else "CLEAR",
                style = Typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace),
                color = if (near) MaterialTheme.colorScheme.error else primaryColor
            )
            Text(
                text = if (distance >= 0f) String.format(java.util.Locale.US, "%.1f cm", distance) else "—",
                style = Typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Drawing Composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun RadarHUDVisualizer(
    magneticCalibrated: Float,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )
    val pulseScale by animateFloatAsState(
        targetValue = if (magneticCalibrated > 25f) 1.15f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "HUDPulse"
    )

    Box(
        modifier = modifier
            .size(230.dp)
            .border(1.dp, primaryColor.copy(alpha = 0.2f), RoundedCornerShape(115.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = radius, style = Stroke(width = 2f))
            drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = radius * 0.66f, style = Stroke(width = 2f))
            drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = radius * 0.33f,
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            drawLine(color = primaryColor.copy(alpha = 0.1f), start = Offset(0f, center.y),
                end = Offset(size.width, center.y), strokeWidth = 2f)
            drawLine(color = primaryColor.copy(alpha = 0.1f), start = Offset(center.x, 0f),
                end = Offset(center.x, size.height), strokeWidth = 2f)
            val angleRad = Math.toRadians(sweepAngle.toDouble())
            val sweepGradient = Brush.sweepGradient(
                colors = listOf(primaryColor.copy(alpha = 0.35f), primaryColor.copy(alpha = 0.0f)),
                center = center
            )
            rotate(degrees = sweepAngle) {
                drawArc(brush = sweepGradient, startAngle = -30f, sweepAngle = 30f, useCenter = true, size = size)
            }
            drawCircle(color = primaryColor, radius = 4f, center = center)
            if (magneticCalibrated > 10f) {
                val indicatorStrengthRatio = (magneticCalibrated / 250f).coerceIn(0.1f, 0.95f)
                val targetRadius = radius * indicatorStrengthRatio
                val targetOffset = Offset(
                    x = (center.x + targetRadius * cos(angleRad + 0.4)).toFloat(),
                    y = (center.y + targetRadius * sin(angleRad + 0.4)).toFloat()
                )
                drawCircle(color = Color.Red.copy(alpha = 0.7f), radius = 8f * pulseScale, center = targetOffset)
                drawCircle(color = Color.Red.copy(alpha = 0.2f), radius = 20f * pulseScale, center = targetOffset)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Default.Sensors, contentDescription = null,
                tint = if (magneticCalibrated > 35f) Color.Red else primaryColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.0f", magneticCalibrated),
                style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 28.sp),
                color = if (magneticCalibrated > 35f) Color.Red else primaryColor
            )
            Text("Δ uT", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun HistoryGraph(history: List<Float>, primaryColor: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(95.dp)
            .background(Color.Black.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(0.5.dp, primaryColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
    ) {
        if (history.isEmpty()) return@Canvas
        val maxVal = maxOf(100f, history.maxOrNull() ?: 100f)
        val path = Path(); val fillPath = Path()
        val stepX = size.width / 59f
        val startOffset = 60 - history.size
        history.forEachIndexed { index, value ->
            val screenX = (startOffset + index) * stepX
            val screenY = size.height - (value / maxVal) * size.height
            if (index == 0) {
                path.moveTo(screenX, screenY)
                fillPath.moveTo(screenX, size.height)
                fillPath.lineTo(screenX, screenY)
            } else {
                path.lineTo(screenX, screenY)
                fillPath.lineTo(screenX, screenY)
            }
            if (index == history.lastIndex) { fillPath.lineTo(screenX, size.height); fillPath.close() }
        }
        drawPath(path = fillPath, brush = Brush.verticalGradient(
            colors = listOf(primaryColor.copy(alpha = 0.25f), primaryColor.copy(alpha = 0.0f))))
        drawPath(path = path, color = primaryColor, style = Stroke(width = 4f, join = StrokeJoin.Round))
    }
}

@Composable
fun SpiritLevelVisualizer(
    pitch: Float,
    roll: Float,
    primaryColor: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 72.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val maxDeviation = this.size.width / 2 - 8f
            drawCircle(color = primaryColor.copy(alpha = 0.15f), radius = 12f, style = Stroke(width = 2f))
            drawLine(color = primaryColor.copy(alpha = 0.12f), start = Offset(4f, center.y),
                end = Offset(this.size.width - 4f, center.y), strokeWidth = 1f)
            drawLine(color = primaryColor.copy(alpha = 0.12f), start = Offset(center.x, 4f),
                end = Offset(center.x, this.size.height - 4f), strokeWidth = 1f)
            val offsetX = (roll / 15f).coerceIn(-1f, 1f) * maxDeviation
            val offsetY = (pitch / 15f).coerceIn(-1f, 1f) * maxDeviation
            val bubbleCenter = Offset(center.x + offsetX, center.y + offsetY)
            val isLevel = kotlin.math.abs(pitch) < 2.0f && kotlin.math.abs(roll) < 2.0f
            val bubbleColor = if (isLevel) primaryColor else Color.Red.copy(alpha = 0.7f)
            drawCircle(color = bubbleColor, radius = 7.5f, center = bubbleCenter)
            drawCircle(color = bubbleColor.copy(alpha = 0.25f), radius = 11f, center = bubbleCenter)
        }
    }
}

private fun BoxBorder(color: Color) = BorderStroke(1.dp, color)

@Composable
private fun MicPermissionPromptCard(
    onRequestPermission: () -> Unit,
    primaryColor: Color,
    surfaceContainer: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.HearingDisabled,
                contentDescription = "Permission Required",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "MICROPHONE REQUIRED",
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = "Recording permission is needed to measure ambient decibels (dB SPL) in real-time.",
                style = Typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "GRANT ACCESS",
                    style = Typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
private fun SoundSensorContent(
    dbSpl: Float,
    history: List<Float>,
    primaryColor: Color,
    surfaceContainer: Color
) {
    val category = when {
        dbSpl < 30f  -> "VERY QUIET"
        dbSpl < 45f  -> "QUIET"
        dbSpl < 60f  -> "MODERATE"
        dbSpl < 75f  -> "LOUD"
        dbSpl < 90f  -> "VERY LOUD"
        else         -> "HEARING RISK"
    }

    val warningColor = when {
        dbSpl < 60f  -> primaryColor
        dbSpl < 75f  -> Color(0xFFFFB300) // Amber
        else         -> MaterialTheme.colorScheme.error
    }

    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WaveOffset"
    )

    DecibelHUDVisualizer(
        dbSpl = dbSpl,
        primaryColor = warningColor,
        waveOffset = waveOffset
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AUDIO WAVEFORM", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            WaveformBouncer(dbSpl = dbSpl, color = warningColor)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${dbSpl.toInt()} dB SPL",
                style = Typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace),
                color = warningColor
            )
            Text(
                text = category,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { dbSpl / 120f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = warningColor,
                trackColor = warningColor.copy(alpha = 0.15f)
            )
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = surfaceContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SOUND HISTORY (dB)", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            HistoryGraph(history = history, primaryColor = warningColor)
        }
    }
}

@Composable
private fun WaveformBouncer(
    dbSpl: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val barCount = 20
    val intensity = (dbSpl / 120f).coerceIn(0f, 1f)
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val envelope = 1f - abs(i - (barCount - 1) / 2f) / ((barCount - 1) / 2f)
            val modifierVal = when (i % 4) {
                0 -> 0.7f
                1 -> 1.2f
                2 -> 0.9f
                else -> 0.5f
            }
            val targetHeight = (5.dp + (50.dp * intensity * envelope * modifierVal))
            val animatedHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
                label = "BarHeight_$i"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(animatedHeight)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(color, color.copy(alpha = 0.3f))
                        ),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun DecibelHUDVisualizer(
    dbSpl: Float,
    primaryColor: Color,
    waveOffset: Float,
    modifier: Modifier = Modifier
) {
    val intensity = (dbSpl / 120f).coerceIn(0f, 1f)
    val pulseScale by animateFloatAsState(
        targetValue = 1f + (intensity * 0.15f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "DbHUDScale"
    )

    Box(
        modifier = modifier
            .size(230.dp)
            .border(1.dp, primaryColor.copy(alpha = 0.2f), RoundedCornerShape(115.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2
            
            drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = radius, style = Stroke(width = 2f))
            drawCircle(color = primaryColor.copy(alpha = 0.12f), radius = radius * 0.75f, style = Stroke(width = 2f))
            
            val waveRadius = radius * 0.75f
            val path = Path()
            val points = 80
            for (x in 0..points) {
                val fraction = x.toFloat() / points
                val angle = fraction * 2f * Math.PI.toFloat()
                val sine = sin(angle * 4f + waveOffset) * (8f + (30f * intensity))
                val r = waveRadius + sine
                val drawX = center.x + r * cos(angle)
                val drawY = center.y + r * sin(angle)
                if (x == 0) {
                    path.moveTo(drawX, drawY)
                } else {
                    path.lineTo(drawX, drawY)
                }
            }
            path.close()
            drawPath(path = path, color = primaryColor.copy(alpha = 0.08f))
            drawPath(path = path, color = primaryColor.copy(alpha = 0.4f), style = Stroke(width = 2.5f))

            drawCircle(
                color = primaryColor.copy(alpha = 0.25f),
                radius = radius * 0.5f,
                style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            )
            drawCircle(color = primaryColor, radius = 4f, center = center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(
                imageVector = Icons.Default.Hearing,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = String.format(java.util.Locale.US, "%.0f", dbSpl),
                style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 32.sp),
                color = primaryColor
            )
            Text("dB SPL", style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
