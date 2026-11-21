package com.familysafety.app

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

class KidsModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KidsModeScreen(KidsApi(this)) }
    }
}

private data class KidsPermissionStep(
    val key: String,
    val title: String,
    val reason: String,
    val action: String,
    val runtimePermission: String? = null,
    val requiredForCore: Boolean = false
)

private val kidsPermissionSteps = listOf(
    KidsPermissionStep("LOCATION_FOREGROUND", "Location while using the app", "Shares the Kids phone's location with linked guardians when location sharing is enabled.", "runtime", Manifest.permission.ACCESS_FINE_LOCATION, true),
    KidsPermissionStep("LOCATION_BACKGROUND", "Background location", "Allows opted-in family location updates while the Kids app is not open. Android may require a separate Settings confirmation.", "background"),
    KidsPermissionStep("NOTIFICATIONS", "Notifications", "Shows pairing status, guardian requests, safety notices and visible device-control requests.", "runtime", if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null, true),
    KidsPermissionStep("USAGE_ACCESS", "App usage summary", "Optional app-time summary. Android opens its special Usage Access settings; the child can decline.", "usage"),
    KidsPermissionStep("ACCESSIBILITY_CONTROL", "Accessibility service", "Not requested. Remote clicking, swiping or reading other apps is not part of this app's supported controls.", "skip"),
    KidsPermissionStep("CAMERA", "Camera", "Not requested. Family Safety does not secretly access or capture camera images.", "skip"),
    KidsPermissionStep("MICROPHONE", "Microphone", "Not requested. Family Safety does not record ambient audio or calls.", "skip"),
    KidsPermissionStep("CONTACTS", "Contacts", "Not requested. Contacts are not needed for location, safety check-ins or device status.", "skip"),
    KidsPermissionStep("CALL_LOGS", "Call logs", "Not requested. Call-history scraping is not part of this app.", "skip"),
    KidsPermissionStep("PHONE_STATE", "Phone state", "Not requested unless a separately disclosed phone feature is added.", "skip"),
    KidsPermissionStep("SMS", "SMS", "Not requested. Messages and OTPs are not read.", "skip"),
    KidsPermissionStep("PHOTOS_MEDIA", "Photos and media", "Not requested. No photo or media collection is required for core safety features.", "skip"),
    KidsPermissionStep("FILES", "Files", "Not requested. The app does not scan personal files.", "skip"),
    KidsPermissionStep("BLUETOOTH_NEARBY", "Bluetooth nearby devices", "Not required for current location, check-in and device-status features.", "skip"),
    KidsPermissionStep("WIFI_NETWORK_STATE", "Network state", "Reads only whether the device is connected by Wi-Fi or mobile data; it does not read Wi-Fi passwords.", "capability"),
    KidsPermissionStep("BATTERY_OPTIMIZATION", "Battery optimization", "Optional. Review Android battery settings if background updates are being delayed. No silent exemption is requested.", "battery"),
    KidsPermissionStep("DISPLAY_OVER_APPS", "Display over other apps", "Not requested. The app does not draw hidden overlays over other apps.", "skip"),
    KidsPermissionStep("DEVICE_ADMIN_LOCK", "Device administrator / remote lock", "Not requested. This app does not silently take device-admin control or remotely lock the whole phone.", "skip"),
    KidsPermissionStep("EXACT_ALARMS", "Exact alarms", "Not required for the current safety sync. No exact-alarm access is requested.", "skip"),
    KidsPermissionStep("FULL_SCREEN_INTENT", "Full-screen notifications", "Not requested. Safety notifications remain normal, visible Android notifications.", "skip")
)

private class KidsApi(private val context: Context) {
    private val prefs = context.getSharedPreferences("family_kids", Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    private fun key(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("family_kids_token_key", null) as? javax.crypto.SecretKey)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            "family_kids_token_key",
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun saveToken(token: String) {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(token.toByteArray())
        prefs.edit().putString("iv", android.util.Base64.encodeToString(cipher.iv, android.util.Base64.NO_WRAP))
            .putString("token", android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP)).apply()
    }

    private fun token(): String? = runCatching {
        val iv = android.util.Base64.decode(prefs.getString("iv", null), android.util.Base64.NO_WRAP)
        val data = android.util.Base64.decode(prefs.getString("token", null), android.util.Base64.NO_WRAP)
        javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").run {
            init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
            doFinal(data).toString(Charsets.UTF_8)
        }
    }.getOrNull()

    private suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, authenticated: Boolean = true): String =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val connection = (java.net.URL(baseUrl + path).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                if (authenticated) token()?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
            try {
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val content = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Server returned HTTP $code: $content")
                content
            } finally { connection.disconnect() }
        }

    suspend fun bootstrap(): JSONObject {
        if (token() != null) return JSONObject().put("deviceId", prefs.getString("device_id", "")).put("existing", true)
        val fingerprint = prefs.getString("fingerprint", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("fingerprint", it).apply()
        }
        val result = JSONObject(request("/v1/kids/bootstrap", "POST", JSONObject().put("installFingerprint", fingerprint), false))
        saveToken(result.getString("deviceToken"))
        prefs.edit().putString("device_id", result.getString("deviceId")).apply()
        return result
    }

    suspend fun rotateOtp(): JSONObject = JSONObject(request("/v1/kids/pairing-otp/rotate", "POST", JSONObject()))
    suspend fun uploadPermissions(rows: JSONArray): JSONObject = JSONObject(request("/v1/kids/permissions", "POST", JSONObject().put("permissions", rows)))
    suspend fun uploadTelemetry(capturedAt: String, payload: JSONObject): JSONObject =
        JSONObject(request("/v1/kids/telemetry", "POST", JSONObject().put("capturedAt", capturedAt).put("payloadJson", payload.toString())))
    fun deviceId(): String = prefs.getString("device_id", "").orEmpty()
}

private fun permissionGranted(context: Context, key: String): Boolean = when (key) {
    "LOCATION_FOREGROUND" -> context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    "LOCATION_BACKGROUND" -> Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    "NOTIFICATIONS" -> Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    "USAGE_ACCESS" -> (context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager)
        .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    "WIFI_NETWORK_STATE" -> true
    "BATTERY_OPTIMIZATION" -> if (Build.VERSION.SDK_INT >= 23) {
        val power = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        power.isIgnoringBatteryOptimizations(context.packageName)
    } else true
    else -> false
}

private fun permissionSnapshot(context: Context): JSONArray {
    val array = JSONArray()
    kidsPermissionSteps.forEach { step ->
        val granted = permissionGranted(context, step.key)
        val detail = when {
            step.action == "skip" -> "Not requested: not needed for the currently enabled Family Safety features"
            step.action == "capability" -> "Network connectivity state only"
            step.action == "battery" && !granted -> "Optional setting not enabled"
            else -> if (granted) "Granted on Kids device" else "Not granted / not enabled"
        }
        array.put(JSONObject().put("key", step.key).put("granted", granted)
            .put("required", step.requiredForCore).put("detail", detail))
    }
    return array
}

private fun collectKidsTelemetry(context: Context): JSONObject {
    val payload = JSONObject()
    payload.put("batteryPercent", runCatching {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0 && scale > 0) (level * 100 / scale) else JSONObject.NULL
    }.getOrNull() ?: JSONObject.NULL)
    payload.put("charging", runCatching {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> "true"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "false"
            else -> "unknown"
        }
    }.getOrDefault("unknown"))
    payload.put("networkType", runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            else -> "other"
        }
    }.getOrDefault("unknown"))
    if (permissionGranted(context, "LOCATION_FOREGROUND")) {
        runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .firstNotNullOfOrNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            location?.let {
                payload.put("latitude", it.latitude)
                payload.put("longitude", it.longitude)
                payload.put("accuracyMeters", it.accuracy.toDouble())
            }
        }
    }
    if (permissionGranted(context, "USAGE_ACCESS")) {
        runCatching {
            val now = System.currentTimeMillis()
            val stats = (context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager)
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5 * 60 * 1000L, now)
            val latest = stats?.maxByOrNull { it.lastTimeUsed }
            if (latest != null) {
                val label = context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(latest.packageName, 0)).toString()
                payload.put("foregroundAppLabel", label.take(80))
            }
        }
    }
    return payload
}

@Composable
private fun KidsModeScreen(api: KidsApi) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var otp by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var stepIndex by remember { mutableIntStateOf(-1) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("This Kids device shares only the safety information described in each step.") }
    var busy by remember { mutableStateOf(false) }

    val runtimeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Permission granted on this Kids device." else "Permission declined. You can continue without it."
        refresh++
        scope.launch { runCatching { api.uploadPermissions(permissionSnapshot(context)) }.onFailure { error = it.message } }
    }

    LaunchedEffect(Unit) {
        runCatching {
            api.bootstrap()
            val code = api.rotateOtp()
            otp = code.optString("pairingOtp")
            expiry = code.optString("expiresAt")
            ready = true
            api.uploadPermissions(permissionSnapshot(context))
        }.onFailure { error = it.message }
    }

    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1E3A8A), secondary = androidx.compose.ui.graphics.Color(0xFF0D9488))) {
        Surface(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("FAMILY SAFETY • KIDS MODE", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Your device. Your permissions.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("This device sends only the safety data you choose to enable. You can decline optional permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("6-digit Parent pairing code", fontWeight = FontWeight.Bold)
                            Text(if (otp.isBlank()) "Connecting…" else otp, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text("Expires: $expiry", fontSize = 11.sp)
                            OutlinedButton(enabled = ready && !busy, onClick = {
                                scope.launch { busy = true; runCatching { val code = api.rotateOtp(); otp = code.optString("pairingOtp"); expiry = code.optString("expiresAt"); message = "A new pairing code is ready." }.onFailure { error = it.message }; busy = false }
                            }) { Text("Generate new code") }
                        }
                    }
                }
                item { Text("20-step permission setup", fontWeight = FontWeight.Bold, fontSize = 19.sp) }
                item { LinearProgressIndicator(progress = { if (stepIndex < 0) 0f else (stepIndex + 1) / 20f }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Button(enabled = ready && stepIndex < 0, onClick = { stepIndex = 0; message = "Step 1 of 20. Read the explanation before continuing." }, modifier = Modifier.fillMaxWidth()) {
                        Text("Start Setup — 20 steps")
                    }
                }
                if (stepIndex in 0..19) {
                    val step = kidsPermissionSteps[stepIndex]
                    item {
                        Card {
                            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("STEP ${stepIndex + 1} / 20", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                Text(step.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Text(step.reason)
                                Text(if (permissionGranted(context, step.key)) "Current status: ON" else "Current status: OFF / not required", fontWeight = FontWeight.SemiBold)
                                when (step.action) {
                                    "runtime" -> Button(onClick = {
                                        val permission = step.runtimePermission
                                        if (permission == null) { refresh++; stepIndex++; }
                                        else runtimeLauncher.launch(permission)
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Allow on this device") }
                                    "background" -> Button(onClick = {
                                        if (Build.VERSION.SDK_INT >= 29) context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                        else { refresh++; stepIndex++ }
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Open Android permission settings") }
                                    "usage" -> Button(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Open Usage Access settings") }
                                    "battery" -> Button(onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Review battery settings") }
                                    "capability" -> Button(onClick = { refresh++; stepIndex++ }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
                                    else -> Button(onClick = { refresh++; stepIndex++ }, modifier = Modifier.fillMaxWidth()) { Text("Skip — not needed") }
                                }
                                OutlinedButton(onClick = {
                                    refresh++
                                    stepIndex = (stepIndex + 1).coerceAtMost(20)
                                    scope.launch { runCatching { api.uploadPermissions(permissionSnapshot(context)) }.onFailure { error = it.message } }
                                }, modifier = Modifier.fillMaxWidth()) { Text("Next step") }
                            }
                        }
                    }
                } else if (stepIndex >= 20) {
                    item { Card { Column(Modifier.padding(16.dp)) { Text("Setup review complete", fontWeight = FontWeight.Bold); Text("You can revisit Android Settings at any time. Only enabled and supported permissions are reported to the linked Parent.") } } }
                }
                item {
                    Button(enabled = ready && !busy, onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                api.uploadPermissions(permissionSnapshot(context))
                                api.uploadTelemetry(Instant.now().toString(), collectKidsTelemetry(context))
                                message = "Permission status and latest device snapshot sent to the linked Parent, if a valid Permit is active."
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Syncing…" else "Sync with Parent now") }
                }
                item { Text("Device ID: ${api.deviceId()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (error != null) item { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}
