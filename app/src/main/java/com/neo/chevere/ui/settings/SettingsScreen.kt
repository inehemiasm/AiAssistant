package com.neo.chevere.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.neo.chevere.R
import com.neo.chevere.domain.WeatherUnitSystem
import com.neo.chevere.domain.ImageAspectRatio
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.neo.chevere.ui.common.ChevereHaptic
import com.neo.chevere.ui.common.performChevereHaptic
import com.neo.chevere.ui.designsystem.AtmosphericTheme
import com.neo.chevere.ui.designsystem.Typography

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onRadarClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    SettingsContent(
        state = state,
        onIntent = { viewModel.onIntent(it) },
        onBackClick = onBackClick,
        onBenchmarkClick = onBenchmarkClick,
        onRadarClick = onRadarClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    onBackClick: () -> Unit,
    onBenchmarkClick: () -> Unit,
    onRadarClick: () -> Unit
) {
    val viewModel = remember(onIntent) {
        object {
            fun onIntent(intent: SettingsIntent) {
                onIntent(intent)
            }
        }
    }
    val hapticView = LocalView.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.system_settings),
                        style = Typography.titleLarge,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        onBackClick()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.interface_configuration),
                    style = Typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isDarkMode) Icons.Default.DarkMode else Icons.Default.LightMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.high_tech_mode),
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (state.isDarkMode) stringResource(R.string.neural_dark_active) else stringResource(
                                        R.string.standard_light_active
                                    ),
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.isDarkMode,
                            onCheckedChange = {
                                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                viewModel.onIntent(SettingsIntent.UpdateTheme(it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                AtmosphericThemesCard(
                    selectedTheme = state.atmosphericTheme,
                    onThemeSelected = {
                        viewModel.onIntent(SettingsIntent.UpdateAtmosphericTheme(it))
                    }
                )

                Spacer(Modifier.height(12.dp))

                WeatherUnitsCard(
                    selectedUnitSystem = state.weatherUnitSystem,
                    onUnitSelected = {
                        hapticView.performChevereHaptic(ChevereHaptic.Selection)
                        viewModel.onIntent(SettingsIntent.UpdateWeatherUnitSystem(it))
                    }
                )

                Spacer(Modifier.height(12.dp))

                ImageGenerationDefaultsCard(
                    selectedRatio = state.defaultImageAspectRatio,
                    steps = state.defaultImageSteps,
                    guidanceScale = state.defaultImageGuidanceScale,
                    negativePrompt = state.defaultImageNegativePrompt,
                    onRatioSelected = { ratio ->
                        viewModel.onIntent(SettingsIntent.UpdateDefaultImageAspectRatio(ratio))
                    },
                    onStepsChanged = { steps ->
                        viewModel.onIntent(SettingsIntent.UpdateDefaultImageSteps(steps))
                    },
                    onGuidanceScaleChanged = { scale ->
                        viewModel.onIntent(SettingsIntent.UpdateDefaultImageGuidanceScale(scale))
                    },
                    onNegativePromptChanged = { prompt ->
                        viewModel.onIntent(SettingsIntent.UpdateDefaultImageNegativePrompt(prompt))
                    }
                )

                Spacer(Modifier.height(12.dp))

                // Biometric Security Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.biometric_lock),
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.biometric_lock_desc),
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.isBiometricLockEnabled,
                            onCheckedChange = {
                                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                viewModel.onIntent(SettingsIntent.UpdateBiometricLock(it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Download on Wi-Fi Only Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Download models over Wi-Fi only",
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Restricts large on-device model downloads to Wi-Fi networks",
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = state.downloadOnWifiOnly,
                            onCheckedChange = {
                                hapticView.performChevereHaptic(ChevereHaptic.Selection)
                                viewModel.onIntent(SettingsIntent.UpdateDownloadOnWifiOnly(it))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    stringResource(R.string.system_info),
                    style = Typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))

                SystemInfoRow(stringResource(R.string.version_label), state.appVersion)
                SystemInfoRow(stringResource(R.string.engine_label), state.engineInfo)
                SystemInfoRow(stringResource(R.string.protocol_label), state.protocolInfo)

                Spacer(Modifier.height(16.dp))

                // Sensor Radar entry
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            onRadarClick()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Sensor Radar",
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Stud finder, metal detector & spirit level",
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            onBenchmarkClick()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.benchmark_label),
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.benchmark_explanation),
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    stringResource(R.string.safety_privacy),
                    style = Typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                SafetyInfoCard(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.local_first_ai),
                    description = stringResource(R.string.local_first_ai_desc)
                )
                Spacer(Modifier.height(12.dp))
                SafetyInfoCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.offline_processing),
                    description = stringResource(R.string.offline_processing_desc)
                )
                Spacer(Modifier.height(12.dp))
                SafetyInfoCard(
                    icon = Icons.Default.Security,
                    title = stringResource(R.string.release_content_controls),
                    description = stringResource(R.string.release_content_controls_desc)
                )
                Spacer(Modifier.height(12.dp))
                SafetyInfoCard(
                    icon = Icons.Default.Share,
                    title = stringResource(R.string.user_controlled_sharing),
                    description = stringResource(R.string.user_controlled_sharing_desc)
                )
                Spacer(Modifier.height(12.dp))
                SafetyInfoCard(
                    icon = Icons.Default.Storage,
                    title = stringResource(R.string.local_storage_privacy),
                    description = stringResource(R.string.local_storage_privacy_desc)
                )
                Spacer(Modifier.height(12.dp))

                // Privacy Policy External Link Card
                val privacyUrl = stringResource(R.string.privacy_policy_url)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            hapticView.performChevereHaptic(ChevereHaptic.Selection)
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(privacyUrl)
                                )
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // Fallback
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    stringResource(R.string.privacy_policy),
                                    style = Typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.privacy_policy_desc),
                                    style = Typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherUnitsCard(
    selectedUnitSystem: WeatherUnitSystem,
    onUnitSelected: (WeatherUnitSystem) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticView = LocalView.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    expanded = !expanded
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.weather_units),
                    style = Typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        stringResource(R.string.weather_units_desc),
                        style = Typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WeatherUnitSegment(
                            label = stringResource(R.string.weather_units_metric),
                            detail = stringResource(R.string.weather_units_metric_desc),
                            selected = selectedUnitSystem == WeatherUnitSystem.METRIC,
                            modifier = Modifier.weight(1f),
                            onClick = { onUnitSelected(WeatherUnitSystem.METRIC) }
                        )
                        WeatherUnitSegment(
                            label = stringResource(R.string.weather_units_imperial),
                            detail = stringResource(R.string.weather_units_imperial_desc),
                            selected = selectedUnitSystem == WeatherUnitSystem.IMPERIAL,
                            modifier = Modifier.weight(1f),
                            onClick = { onUnitSelected(WeatherUnitSystem.IMPERIAL) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherUnitSegment(
    label: String,
    detail: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = Typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.sp
            )
            Text(
                detail,
                style = Typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SafetyInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticView = LocalView.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    expanded = !expanded
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    title,
                    style = Typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Text(
                    description,
                    style = Typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 40.dp, top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun SystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = Typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AtmosphericThemesCard(
    selectedTheme: AtmosphericTheme,
    onThemeSelected: (AtmosphericTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticView = LocalView.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    expanded = !expanded
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "ATMOSPHERIC PRESETS",
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemePresetSegment(
                            name = "CLASSIC CYAN",
                            primaryColor = Color(0xFF00BFA5),
                            backgroundColor = Color(0xFF07111D),
                            selected = selectedTheme == AtmosphericTheme.CLASSIC_CYAN,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AtmosphericTheme.CLASSIC_CYAN) }
                        )
                        ThemePresetSegment(
                            name = "MATRIX GREEN",
                            primaryColor = Color(0xFF00FF66),
                            backgroundColor = Color(0xFF020904),
                            selected = selectedTheme == AtmosphericTheme.MATRIX_GREEN,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AtmosphericTheme.MATRIX_GREEN) }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemePresetSegment(
                            name = "CYBERPUNK GOLD",
                            primaryColor = Color(0xFFFF9E00),
                            backgroundColor = Color(0xFF0C0700),
                            selected = selectedTheme == AtmosphericTheme.CYBERPUNK_GOLD,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AtmosphericTheme.CYBERPUNK_GOLD) }
                        )
                        ThemePresetSegment(
                            name = "OBSIDIAN DARK",
                            primaryColor = Color(0xFFD2BFFF),
                            backgroundColor = Color(0xFF040306),
                            selected = selectedTheme == AtmosphericTheme.OBSIDIAN_DARK,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeSelected(AtmosphericTheme.OBSIDIAN_DARK) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemePresetSegment(
    name: String,
    primaryColor: Color,
    backgroundColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hapticView = LocalView.current
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    }

    Surface(
        modifier = modifier.clickable {
            hapticView.performChevereHaptic(ChevereHaptic.Selection)
            onClick()
        },
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                name,
                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = primaryColor,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.5f))
                ) {}
                Surface(
                    modifier = Modifier.size(10.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = backgroundColor,
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f))
                ) {}
            }
        }
    }
}

@Composable
private fun ImageGenerationDefaultsCard(
    selectedRatio: ImageAspectRatio,
    steps: Int,
    guidanceScale: Float,
    negativePrompt: String,
    onRatioSelected: (ImageAspectRatio) -> Unit,
    onStepsChanged: (Int) -> Unit,
    onGuidanceScaleChanged: (Float) -> Unit,
    onNegativePromptChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val hapticView = LocalView.current

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    hapticView.performChevereHaptic(ChevereHaptic.Selection)
                    expanded = !expanded
                }
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "IMAGE GENERATION DEFAULTS",
                    style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    // Aspect Ratio selection
                    Text(
                        "ASPECT RATIO",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageAspectRatioSegment(
                                ratio = ImageAspectRatio.SQUARE_1_1,
                                selected = selectedRatio == ImageAspectRatio.SQUARE_1_1,
                                modifier = Modifier.weight(1f),
                                onClick = { onRatioSelected(ImageAspectRatio.SQUARE_1_1) }
                            )
                            ImageAspectRatioSegment(
                                ratio = ImageAspectRatio.LANDSCAPE_16_9,
                                selected = selectedRatio == ImageAspectRatio.LANDSCAPE_16_9,
                                modifier = Modifier.weight(1f),
                                onClick = { onRatioSelected(ImageAspectRatio.LANDSCAPE_16_9) }
                            )
                            ImageAspectRatioSegment(
                                ratio = ImageAspectRatio.PORTRAIT_9_16,
                                selected = selectedRatio == ImageAspectRatio.PORTRAIT_9_16,
                                modifier = Modifier.weight(1f),
                                onClick = { onRatioSelected(ImageAspectRatio.PORTRAIT_9_16) }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageAspectRatioSegment(
                                ratio = ImageAspectRatio.LANDSCAPE_4_3,
                                selected = selectedRatio == ImageAspectRatio.LANDSCAPE_4_3,
                                modifier = Modifier.weight(1f),
                                onClick = { onRatioSelected(ImageAspectRatio.LANDSCAPE_4_3) }
                            )
                            ImageAspectRatioSegment(
                                ratio = ImageAspectRatio.PORTRAIT_3_4,
                                selected = selectedRatio == ImageAspectRatio.PORTRAIT_3_4,
                                modifier = Modifier.weight(1f),
                                onClick = { onRatioSelected(ImageAspectRatio.PORTRAIT_3_4) }
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Steps selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "INFERENCE STEPS",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = steps.toString(),
                            style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = steps.toFloat(),
                        onValueChange = { onStepsChanged(it.toInt()) },
                        valueRange = 5f..50f,
                        steps = 44, // 50 - 5 = 45 points, steps = 44 cuts it into increments of 1
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    // Guidance Scale selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GUIDANCE SCALE",
                            style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", guidanceScale),
                            style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = guidanceScale,
                        onValueChange = onGuidanceScaleChanged,
                        valueRange = 1.0f..20.0f,
                        steps = 38, // (20 - 1) * 2 = 38 cuts it into 0.5 increments
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    // Negative prompt text field
                    OutlinedTextField(
                        value = negativePrompt,
                        onValueChange = onNegativePromptChanged,
                        label = { Text("DEFAULT NEGATIVE PROMPT", style = Typography.labelSmall.copy(fontSize = 10.sp)) },
                        placeholder = {
                            Text(
                                "e.g. blurry, low quality, distorted",
                                style = Typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            )
                        },
                        textStyle = Typography.bodyMedium,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageAspectRatioSegment(
    ratio: ImageAspectRatio,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val hapticView = LocalView.current
    val background = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)
    }

    Surface(
        modifier = modifier.clickable {
            hapticView.performChevereHaptic(ChevereHaptic.Selection)
            onClick()
        },
        color = background,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = ratio.displayName,
                style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${ratio.pixelWidth}x${ratio.pixelHeight}",
                style = Typography.bodySmall.copy(fontSize = 9.sp, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    com.neo.chevere.ui.designsystem.HighTechAiTheme(darkTheme = true) {
        SettingsContent(
            state = SettingsState(
                isDarkMode = true,
                atmosphericTheme = AtmosphericTheme.CLASSIC_CYAN,
                weatherUnitSystem = WeatherUnitSystem.METRIC,
                isBiometricLockEnabled = false,
                downloadOnWifiOnly = true
            ),
            onIntent = {},
            onBackClick = {},
            onBenchmarkClick = {},
            onRadarClick = {}
        )
    }
}

