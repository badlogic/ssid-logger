package at.mariozechner.ssid_logger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import at.mariozechner.ssid_logger.ui.theme.SsidloggerTheme

class MainActivity : ComponentActivity() {
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for SSID monitoring", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Load saved URL from SharedPreferences
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("endpoint_url", "http://10.0.2.2:3000/log") ?: ""
        
        setContent {
            SsidloggerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        savedUrl = savedUrl,
                        modifier = Modifier.padding(innerPadding),
                        onRequestPermissions = { requestPermissions() },
                        onStartService = { url -> startMonitoring(url) },
                        onStopService = { stopMonitoring() },
                        context = this
                    )
                }
            }
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun startMonitoring(url: String) {
        // Save URL to SharedPreferences
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        prefs.edit().putString("endpoint_url", url).apply()
        
        // Start the foreground service
        val intent = Intent(this, WifiMonitorService::class.java).apply {
            putExtra("endpoint_url", url)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "Monitoring started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopMonitoring() {
        val intent = Intent(this, WifiMonitorService::class.java)
        stopService(intent)
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun MainScreen(
    savedUrl: String,
    modifier: Modifier = Modifier,
    onRequestPermissions: () -> Unit,
    onStartService: (String) -> Unit,
    onStopService: () -> Unit,
    context: Context
) {
    var urlText by remember { mutableStateOf(savedUrl) }
    var isMonitoring by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "SSID Logger",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Monitor WiFi network changes and send logs to your server",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = urlText,
            onValueChange = { 
                urlText = it
                urlError = false
            },
            label = { Text("Endpoint URL") },
            placeholder = { Text("http://10.0.2.2:3000/log") },
            isError = urlError,
            supportingText = {
                if (urlError) {
                    Text("Please enter a valid URL")
                } else {
                    Text("For emulator use 10.0.2.2 instead of localhost")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Check permissions button
        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permissions")
        }
        
        // Start/Stop monitoring button
        Button(
            onClick = {
                if (isMonitoring) {
                    onStopService()
                    isMonitoring = false
                } else {
                    // Basic URL validation
                    if (urlText.isBlank() || !urlText.startsWith("http")) {
                        urlError = true
                    } else {
                        onStartService(urlText)
                        isMonitoring = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isMonitoring) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
        ) {
            Text(if (isMonitoring) "Stop Monitoring" else "Start Monitoring")
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isMonitoring) "✅ Monitoring active" else "⏸️ Not monitoring",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isMonitoring) {
                    Text(
                        text = "Endpoint: $urlText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}