package at.mariozechner.ssid_logger

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
            // Check if we still need background location
            if (!hasBackgroundLocationPermission()) {
                showBackgroundLocationDialog()
            } else {
                savePermissionsGranted(true)
                Toast.makeText(this, "All permissions granted!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Permissions required for SSID monitoring", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Load saved state
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("endpoint_url", "http://10.0.2.2:3000/log") ?: ""
        val permissionsGranted = prefs.getBoolean("permissions_granted", false)
        
        // Check if service is actually running, if not clear the saved state
        if (!isServiceRunning()) {
            prefs.edit().putBoolean("service_running", false).apply()
        }
        
        setContent {
            SsidloggerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        savedUrl = savedUrl,
                        permissionsGranted = permissionsGranted,
                        modifier = Modifier.padding(innerPadding),
                        onStartService = { url -> startMonitoring(url) },
                        onStopService = { stopMonitoring() },
                        onOpenSettings = { openAppSettings() },
                        context = this
                    )
                }
            }
        }
    }
    
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WifiMonitorService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
    
    fun hasAllPermissions(): Boolean {
        val basicPermissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        val allBasicGranted = basicPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (!allBasicGranted) return false
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        
        // Check background location for Android 10+
        return hasBackgroundLocationPermission()
    }
    
    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == 
                PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older versions
        }
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Note: We don't request BACKGROUND_LOCATION here as it requires a separate flow
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }
    
    private fun showBackgroundLocationDialog() {
        // In a real app, you'd show a dialog explaining why you need background location
        // For now, we'll just open settings
        Toast.makeText(
            this, 
            "Please enable 'Allow all the time' in Location settings for background monitoring", 
            Toast.LENGTH_LONG
        ).show()
        openAppSettings()
    }
    
    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }
    
    private fun savePermissionsGranted(granted: Boolean) {
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("permissions_granted", granted).apply()
    }
    
    private fun startMonitoring(url: String) {
        // Check permissions first
        if (!hasAllPermissions()) {
            requestPermissions()
            Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Save URL and service state to SharedPreferences
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("endpoint_url", url)
            .putBoolean("service_running", true)
            .apply()
        
        // Save that permissions have been granted
        savePermissionsGranted(true)
        
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
        
        // Update service state
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_running", false).apply()
        
        Toast.makeText(this, "Monitoring stopped", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // Check permissions status when returning from settings
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        if (hasAllPermissions() && !prefs.getBoolean("permissions_granted", false)) {
            savePermissionsGranted(true)
        }
    }
}

@Composable
fun MainScreen(
    savedUrl: String,
    permissionsGranted: Boolean,
    modifier: Modifier = Modifier,
    onStartService: (String) -> Unit,
    onStopService: () -> Unit,
    onOpenSettings: () -> Unit,
    context: Context
) {
    var urlText by remember { mutableStateOf(savedUrl) }
    var isMonitoring by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(permissionsGranted) }
    
    // Check if service is already running and permissions
    LaunchedEffect(Unit) {
        val activity = context as MainActivity
        hasPermissions = activity.hasAllPermissions()
        // Check if service is running by checking shared prefs
        val prefs = context.getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        isMonitoring = prefs.getBoolean("service_running", false)
    }
    
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
        
        // Start/Stop monitoring button
        Button(
            onClick = {
                if (isMonitoring) {
                    onStopService()
                    // Update state immediately
                    isMonitoring = false
                    // Also update SharedPreferences
                    val prefs = context.getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("service_running", false).apply()
                } else {
                    // Basic URL validation
                    if (urlText.isBlank() || !urlText.startsWith("http")) {
                        urlError = true
                    } else {
                        onStartService(urlText)
                        // Update state immediately
                        isMonitoring = true
                        // Also update SharedPreferences
                        val prefs = context.getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("service_running", true).apply()
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
        
        // Permission warning card if needed
        if (!hasPermissions) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️ Permissions Required",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Background location permission needed for WiFi monitoring",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Manual step required:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "1. Tap 'Open Settings' below\n" +
                              "2. Go to Permissions → Location\n" +
                              "3. Select 'Allow all the time'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Open Settings")
                    }
                }
            }
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
                if (!hasPermissions) {
                    Text(
                        text = "❌ Missing permissions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "✅ All permissions granted",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
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