package at.mariozechner.ssid_logger

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class WifiMonitorService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "WifiMonitorChannel"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "WifiMonitorService"
    }
    
    private var endpointUrl: String = ""
    private var previousSSID: String? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        endpointUrl = intent?.getStringExtra("endpoint_url") ?: ""
        
        if (endpointUrl.isEmpty()) {
            Log.e(TAG, "No endpoint URL provided")
            stopSelf()
            return START_NOT_STICKY
        }
        
        Log.d(TAG, "Starting WiFi monitoring with endpoint: $endpointUrl")
        
        // Start foreground service
        startForeground()
        
        // Register network callback
        registerNetworkCallback()
        
        // Get initial SSID
        previousSSID = getCurrentSSID()
        Log.d(TAG, "Initial SSID: $previousSSID")
        
        // Send initial log to confirm monitoring started
        serviceScope.launch {
            sendStartupLog(previousSSID)
        }
        
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "WiFi Monitoring Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors WiFi SSID changes"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
    
    private fun startForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSID Logger Active")
            .setContentText("Monitoring WiFi network changes")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
    
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                checkSSIDChange()
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                checkSSIDChange()
            }
        }
        
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register network callback: ${e.message}")
        }
    }
    
    private fun checkSSIDChange() {
        val currentSSID = getCurrentSSID()
        
        if (currentSSID != null && currentSSID != previousSSID) {
            Log.d(TAG, "SSID changed from $previousSSID to $currentSSID")
            
            // Send log to endpoint
            serviceScope.launch {
                sendSSIDChangeLog(previousSSID, currentSSID)
            }
            
            previousSSID = currentSSID
        }
    }
    
    private fun getCurrentSSID(): String? {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo != null && wifiInfo.ssid != "<unknown ssid>") {
                // Remove quotes from SSID
                wifiInfo.ssid.replace("\"", "")
            } else {
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied for getting SSID: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting SSID: ${e.message}")
            null
        }
    }
    
    private suspend fun sendStartupLog(currentSSID: String?) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Attempting to connect to: $endpointUrl")
                val url = URL(endpointUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                
                // Create JSON payload for startup
                val json = JSONObject().apply {
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                    put("event", "monitoring_started")
                    put("currentSSID", currentSSID ?: "none")
                    put("message", "SSID monitoring service started")
                }
                
                // Send request
                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray())
                    os.flush()
                }
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Successfully sent startup log")
                    // Show success toast on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            applicationContext,
                            "Connected to logging server",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Log.e(TAG, "Failed to send startup log. Response code: $responseCode")
                    showErrorToast("Server responded with error: $responseCode")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending startup log: ${e.message}")
                showErrorToast("Cannot connect to server: Check URL and network")
            }
        }
    }
    
    private suspend fun sendSSIDChangeLog(oldSSID: String?, newSSID: String) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(endpointUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                
                // Create JSON payload
                val json = JSONObject().apply {
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                    put("previousSSID", oldSSID ?: "none")
                    put("newSSID", newSSID)
                }
                
                // Send request
                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray())
                    os.flush()
                }
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Successfully sent SSID change log")
                } else {
                    Log.e(TAG, "Failed to send log. Response code: $responseCode")
                    showErrorNotification("Failed to send log to server")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending SSID change log: ${e.message}")
                showErrorNotification("Cannot reach endpoint: $endpointUrl")
            }
        }
    }
    
    private fun showErrorToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(
                applicationContext,
                message,
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    private fun showErrorNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSID Logger Error")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        notificationManager?.notify(NOTIFICATION_ID + 1, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Send shutdown log before destroying
        runBlocking {
            sendShutdownLog(getCurrentSSID())
        }
        
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering network callback: ${e.message}")
        }
        
        // Clear service running flag
        val prefs = getSharedPreferences("ssid_logger", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("service_running", false).apply()
        
        serviceScope.cancel()
        Log.d(TAG, "Service destroyed")
    }
    
    private suspend fun sendShutdownLog(currentSSID: String?) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(endpointUrl)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                
                // Create JSON payload for shutdown
                val json = JSONObject().apply {
                    put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date()))
                    put("event", "monitoring_stopped")
                    put("currentSSID", currentSSID ?: "none")
                    put("message", "SSID monitoring service stopped")
                }
                
                // Send request
                connection.outputStream.use { os ->
                    os.write(json.toString().toByteArray())
                    os.flush()
                }
                
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Successfully sent shutdown log")
                } else {
                    Log.e(TAG, "Failed to send shutdown log. Response code: $responseCode")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending shutdown log: ${e.message}")
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}