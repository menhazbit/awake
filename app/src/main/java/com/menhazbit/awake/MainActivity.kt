package com.menhazbit.awake

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.menhazbit.awake.BuildConfig
import com.menhazbit.awake.manager.WakeLockManager
import com.menhazbit.awake.service.WakeLockService
import com.menhazbit.awake.ui.theme.AwakeTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwakeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AwakeScreen(
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermission = { requestWriteSettingsPermission() }
                    )
                }
            }
        }
    }
    
    private fun requestWriteSettingsPermission() {
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
    

}

@Composable
fun AwakeScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},

) {
    val context = LocalContext.current
    var isWakeLockActive by remember { mutableStateOf(false) }
    var hasWritePermission by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var currentScreenTimeout by remember { mutableStateOf(0) }
    
    LaunchedEffect(Unit) {
        while (true) {
            val wakeLockManager = WakeLockManager.getInstance(context)
            isWakeLockActive = wakeLockManager.isWakeLockHeld()
            hasWritePermission = Settings.System.canWrite(context)
            currentScreenTimeout = wakeLockManager.getCurrentScreenTimeout()
            delay(1000)
        }
    }
    
    val iconColor by animateColorAsState(
        targetValue = if (isWakeLockActive) 
            MaterialTheme.colorScheme.primary
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Main content card - completely consistent layout
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.coffee),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = iconColor
                    )
                }
                
                // Title and status
                Text(
                    text = "Awake",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Light
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isWakeLockActive)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (isWakeLockActive) "ON" else "OFF",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isWakeLockActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Status description - fixed height
                Text(
                    text = if (isWakeLockActive) {
                        "Screen stays on"
                    } else {
                        "Screen sleeps normally"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2
                )
                
                // Action button - same position always
                if (isWakeLockActive) {
                    OutlinedButton(
                        onClick = { WakeLockService.stopWakeLock(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Turn Off",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    FilledTonalButton(
                        onClick = { 
                            if (hasWritePermission) {
                                WakeLockService.startWakeLock(context)
                            } else {
                                onRequestPermission()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (hasWritePermission) "Keep Awake" else "Grant Permission",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                
                // System info - fixed height container, always same space
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp), // Fixed height to prevent layout shifts
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Screen timeout row - always present but conditionally visible content
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Screen timeout:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTimeout(currentScreenTimeout),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Permission row - always visible
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "System permission:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (hasWritePermission) "Granted" else "Required",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (hasWritePermission) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Footer - minimal version only
        Text(
            text = "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

// Utility function to format timeout in human-readable format
private fun formatTimeout(timeoutMs: Int): String = when (timeoutMs) {
    Int.MAX_VALUE -> "Never"
    15000 -> "15s"
    30000 -> "30s"
    60000 -> "1m"
    120000 -> "2m"
    300000 -> "5m"
    600000 -> "10m"
    else -> when {
        timeoutMs < 1000 -> "${timeoutMs}ms"
        timeoutMs < 60000 -> "${timeoutMs / 1000}s"
        timeoutMs % 60000 == 0 -> "${timeoutMs / 60000}m"
        else -> "${timeoutMs / 60000}m ${(timeoutMs % 60000) / 1000}s"
    }
}