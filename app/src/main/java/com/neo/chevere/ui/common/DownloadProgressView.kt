package com.neo.chevere.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neo.chevere.R
import com.neo.chevere.core.Constants
import com.neo.chevere.ui.designsystem.AmbientGlow
import com.neo.chevere.ui.designsystem.Typography

/**
 * A visually rich view for displaying the progress of a model download.
 *
 * It features a large circular progress indicator with the percentage in the
 * center, the name of the model being downloaded, and a status description.
 *
 * @param modelName The name of the AI model being downloaded.
 * @param progress The current download progress as a percentage (0-100).
 */
@Composable
fun DownloadProgressView(modelName: String, progress: Int) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AmbientGlow(MaterialTheme.colorScheme.primary, Modifier.align(Alignment.Center).size(400.dp))
        
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.size(120.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 8.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Text(
                    text = "$progress%", 
                    color = MaterialTheme.colorScheme.onSurface, 
                    style = Typography.headlineMedium.copy(fontSize = 24.sp)
                )
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                stringResource(R.string.synthesizing_core), 
                color = MaterialTheme.colorScheme.onSurface, 
                style = Typography.labelSmall.copy(letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            )
            
            Text(
                modelName.replace(Constants.ModelFiles.LITERTLM_EXTENSION, "").uppercase(),
                color = MaterialTheme.colorScheme.primary, 
                style = Typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(top = 4.dp)
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                stringResource(R.string.synthesizing_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
