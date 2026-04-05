package com.neo.aiassistant

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.aiassistant.domain.ChatMessage
import com.neo.aiassistant.ui.theme.AiAssistantTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAssistantTheme {
                ChatScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    val modelFile = File(context.filesDir, state.selectedModel)

    LaunchedEffect(Unit) {
        if (!state.isReady && !state.isLoading && modelFile.exists()) {
            viewModel.onIntent(ChatIntent.Initialize(modelFile.absolutePath))
        }
        
        viewModel.effect.collect { effect ->
            when (effect) {
                ChatEffect.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
                is ChatEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            FuturisticTopBar(
                isThinking = state.isLoading,
                selectedModel = state.selectedModel,
                availableModels = state.availableModels.keys.toList(),
                onModelSelected = { modelName ->
                    viewModel.onIntent(ChatIntent.SwitchModel(modelName, context.filesDir.absolutePath))
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                text = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    viewModel.onIntent(ChatIntent.SendMessage(inputText))
                    inputText = ""
                },
                enabled = state.isReady && !state.isLoading && !state.isDownloading
            )
        },
        containerColor = Color(0xFF0B0E14)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (state.isDownloading) {
                DownloadProgressView(state.selectedModel, state.downloadProgress ?: 0)
            } else if (!modelFile.exists()) {
                BeautifulModelMissingView(
                    path = modelFile.absolutePath,
                    isFetching = state.isFetchingModels,
                    error = state.error,
                    onDownloadClick = { modelName ->
                        viewModel.onIntent(ChatIntent.DownloadModel(modelName, context.filesDir.absolutePath))
                    },
                    onClearError = {
                        viewModel.onIntent(ChatIntent.ClearError)
                    }
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    MessageList(
                        messages = state.messages,
                        modifier = Modifier.weight(1f),
                        listState = listState
                    )
                    
                    QuantumThinkingIndicator(state.isLoading)
                }
            }

            if (state.error != null && modelFile.exists()) {
                ErrorSnackbar(state.error!!) {
                    viewModel.onIntent(ChatIntent.ClearError)
                }
            }
        }
    }
}

@Composable
fun DownloadProgressView(modelName: String, progress: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier.size(80.dp),
            color = Color(0xFF00E5FF),
            strokeWidth = 6.dp,
            trackColor = Color.DarkGray
        )
        Spacer(Modifier.height(24.dp))
        Text("DOWNLOADING CORE...", color = Color.White, fontWeight = FontWeight.Bold)
        Text(modelName.replace(".litertlm", "").uppercase(), color = Color(0xFF00E5FF), fontSize = 12.sp)
        Text("$progress%", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Synthesizing neural weights. This might take a few minutes depending on your uplink speed.",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuturisticTopBar(
    isThinking: Boolean,
    selectedModel: String,
    availableModels: List<String>,
    onModelSelected: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "energyLine")
    val lineOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lineOffset"
    )

    Column {
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { if (!isThinking) showMenu = true }
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GEMMA CORE", fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
                        Icon(Icons.Default.ExpandMore, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    Text(selectedModel.replace(".litertlm", "").uppercase(), color = Color(0xFF00E5FF).copy(alpha = 0.7f), fontSize = 10.sp)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF151921))) {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = { Text(model.replace(".litertlm", "").uppercase(), color = if (model == selectedModel) Color(0xFF00E5FF) else Color.White) },
                            onClick = { onModelSelected(model); showMenu = false }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color(0xFF151921))
        )
        if (isThinking) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.DarkGray)) {
                Box(Modifier.fillMaxWidth(0.3f).fillMaxHeight().align(Alignment.CenterStart)
                    .graphicsLayer { translationX = (lineOffset + 1f) * 500f }
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xFF00E5FF), Color.Transparent))))
            }
        }
    }
}

@Composable
fun QuantumThinkingIndicator(isThinking: Boolean) {
    AnimatedVisibility(visible = isThinking, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
                val infiniteTransition = rememberInfiniteTransition(label = "quantum")
                repeat(3) { i ->
                    val scale by infiniteTransition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, delayMillis = i * 400, easing = LinearOutSlowInEasing)), "scale$i")
                    val alpha by infiniteTransition.animateFloat(1f, 0f, infiniteRepeatable(tween(1200, delayMillis = i * 400, easing = LinearOutSlowInEasing)), "alpha$i")
                    Canvas(Modifier.fillMaxSize()) { 
                        drawCircle(
                            color = Color(0xFF00E5FF), 
                            radius = (size.minDimension / 2) * scale, 
                            alpha = alpha, 
                            style = Stroke(width = 2.dp.toPx())
                        ) 
                    }
                }
                Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
            }
            Text("SYNTHESIZING...", color = Color(0xFF00E5FF), letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun MessageList(messages: List<ChatMessage>, modifier: Modifier = Modifier, listState: LazyListState) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(messages) { message -> FuturisticChatBubble(message) }
    }
}

@Composable
fun FuturisticChatBubble(message: ChatMessage) {
    val isUser = message.isUser
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) Color(0xFF1A237E) else Color(0xFF1E242E)
    val borderColor = if (isUser) Color(0xFF3949AB) else Color(0xFF37474F)

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Surface(
            color = bubbleColor, contentColor = Color.White,
            shape = RoundedCornerShape(if (isUser) 16.dp else 4.dp, 16.dp, 16.dp, if (isUser) 4.dp else 16.dp),
            modifier = Modifier.widthIn(max = 300.dp).border(1.dp, borderColor, RoundedCornerShape(if (isUser) 16.dp else 4.dp, 16.dp, 16.dp, if (isUser) 4.dp else 16.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(text = message.text, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp))
                if (!isUser && message.inferenceTimeMs != null) {
                    val seconds = message.inferenceTimeMs / 1000.0
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp).background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Icon(Icons.Default.Timer, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(String.format(Locale.getDefault(), "%.2fs", seconds), color = Color(0xFF00E5FF), fontSize = 10.sp)
                    }
                }
            }
        }
        Text(if (isUser) "OPERATOR" else "GEMMA_UNIT_04", color = if (isUser) Color(0xFF7986CB) else Color(0xFF90A4AE), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ChatInputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit, enabled: Boolean) {
    Surface(color = Color(0xFF151921), modifier = Modifier.navigationBarsPadding()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth().imePadding(), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(1.dp, if (enabled) Color(0xFF37474F) else Color.Transparent, RoundedCornerShape(8.dp)),
                placeholder = { Text("Enter command...", color = Color.Gray) }, enabled = enabled,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color(0xFF0B0E14), unfocusedContainerColor = Color(0xFF0B0E14), disabledContainerColor = Color(0xFF0B0E14), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
            Spacer(Modifier.width(12.dp))
            val isSendEnabled = enabled && text.isNotBlank()
            IconButton(onClick = { if (isSendEnabled) onSend() }, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(if (isSendEnabled) Color(0xFF00E5FF) else Color(0xFF1E242E))) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (isSendEnabled) Color.Black else Color.Gray)
            }
        }
    }
}

@Composable
fun BeautifulModelMissingView(
    path: String, 
    isFetching: Boolean, 
    error: String?,
    onDownloadClick: (String) -> Unit,
    onClearError: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp).background(Color(0xFF0B0E14)), 
        verticalArrangement = Arrangement.Center, 
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isFetching) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
            Spacer(Modifier.height(16.dp))
            Text("SCANNING REPOSITORIES...", color = Color.Gray, fontSize = 12.sp)
        } else if (error != null) {
            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("MISSION FAILURE", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                error, 
                color = Color.Gray, 
                fontSize = 14.sp, 
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onClearError, 
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black), 
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Refresh, null)
                Spacer(Modifier.width(8.dp))
                Text("RETRY UPLINK")
            }
        } else {
            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFF00E5FF), modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(24.dp))
            Text("CORE MODEL MISSING", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text("Select neural core to download:", color = Color.Gray, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            
            ModelDownloadOption(
                name = "E2B (Small)",
                desc = "Fast inference, lower accuracy",
                onClick = { onDownloadClick("gemma-4-E2B-it.litertlm") }
            )
            
            Spacer(Modifier.height(16.dp))
            
            ModelDownloadOption(
                name = "E4B (Standard)",
                desc = "Better reasoning, higher resources",
                onClick = { onDownloadClick("gemma-4-E4B-it.litertlm") }
            )
        }
    }
}

@Composable
fun ModelDownloadOption(name: String, desc: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
        border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Text(desc, color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
fun ErrorSnackbar(message: String, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Snackbar(action = { TextButton(onClick = onDismiss) { Text("REBOOT", color = Color(0xFF00E5FF)) } }, containerColor = Color(0xFF2C1414), contentColor = Color.Red) { Text(message) }
    }
}
