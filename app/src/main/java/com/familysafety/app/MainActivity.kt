package com.familysafety.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF1E3A8A)
private val Blue = Color(0xFF2563EB)
private val Teal = Color(0xFF0D9488)
private val Canvas = Color(0xFFF8FAFC)
private val Ink = Color(0xFF0F172A)
private val Muted = Color(0xFF475569)
private val Danger = Color(0xFFEF4444)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val api = ParentApi(this)
        setContent { FamilyParentApp(api) }
    }
}

private enum class Tab(val label: String) {
    Home("Home"), Devices("Devices"), Activity("Activity"), Alerts("Alerts"), Settings("Settings")
}

@Composable
private fun FamilyParentApp(api: ParentApi) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Home) }
    var page by remember { mutableStateOf("Dashboard") }
    var deviceId by remember { mutableStateOf("") }
    var devices by remember { mutableStateOf(JSONArray()) }
    var history by remember { mutableStateOf(JSONArray()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf("Connecting securely…") }
    var pdfPreference by remember { mutableStateOf(false) }
    var retentionHours by remember { mutableIntStateOf(168) }
    var search by remember { mutableStateOf("") }
    var selectedDevice by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                deviceId = api.bootstrap()
                devices = api.devices()
                val prefs = api.historyPreferences()
                pdfPreference = prefs.optBoolean("savePdfBeforeDelete", false)
                retentionHours = prefs.optInt("retentionHours", 168)
                info = if (devices.length() == 0) "No Kids device is linked yet" else "${devices.length()} linked device(s)"
            } catch (e: Exception) {
                error = e.message ?: "Unable to connect to Family Safety server"
                info = "Offline / server unavailable"
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Canvas, surface = Color.White, error = Danger)) {
        Scaffold(
            containerColor = Canvas,
            topBar = {
                Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Navy, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Shield, null, tint = Color.White, modifier = Modifier.padding(9.dp).size(22.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("FAMILY SAFETY", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(if (page == "Dashboard") tab.label else page, color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { refresh() }) { Text("EN / HI") }
                        IconButton(onClick = { tab = Tab.Alerts; page = "Dashboard" }) {
                            Icon(Icons.Default.Notifications, null, tint = Navy)
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Tab.values().forEach { item ->
                        NavigationBarItem(
                            selected = tab == item && page == "Dashboard",
                            onClick = { tab = item; page = "Dashboard" },
                            icon = { Icon(when(item) {
                                Tab.Home -> Icons.Default.Home
                                Tab.Devices -> Icons.Default.Devices
                                Tab.Activity -> Icons.Default.Timeline
                                Tab.Alerts -> Icons.Default.Notifications
                                Tab.Settings -> Icons.Default.Settings
                            }, contentDescription = item.label) },
                            label = { Text(item.label, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (error != null) {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F2))) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Connection unavailable", color = Danger, fontWeight = FontWeight.Bold)
                            Text(error!!, color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { refresh() }) { Text("Retry") }
                        }
                    }
                }
                when {
                    page != "Dashboard" -> DetailPage(page, onBack = { page = "Dashboard" }, api = api, selectedDevice = selectedDevice,
                        pdfPreference = pdfPreference, onPdfPreference = { pdfPreference = it })
                    tab == Tab.Home -> HomePage(info, devices, busy, onAction = { action ->
                        when(action) {
                            "Add Child" -> page = "Child Profile"
                            "Link Device" -> page = "Pair Device"
                            "Check-In" -> page = "Check-In"
                            "Downtime" -> page = "Routines"
                            else -> page = action
                        }
                    }, onDevice = { id -> selectedDevice = id; page = "Child Profile" }, onHistory = { id -> selectedDevice = id; page = "History" })
                    tab == Tab.Devices -> DevicesPage(devices, onAction = { page = it; if (it == "Pair Device") selectedDevice = "" })
                    tab == Tab.Activity -> ActivityPage(devices, onOpen = { id -> selectedDevice = id; page = "History" }, onReports = { page = "Reports" })
                    tab == Tab.Alerts -> AlertsPage(onOpen = { page = it })
                    else -> SettingsPage(search, { search = it }, pdfPreference, retentionHours, { enabled ->
                        pdfPreference = enabled
                        scope.launch { runCatching { val saved = api.saveHistoryPreferences(enabled, retentionHours); pdfPreference = saved.optBoolean("savePdfBeforeDelete", enabled) }.onFailure { error = it.message } }
                    }, { hours ->
                        retentionHours = hours
                        scope.launch { runCatching { val saved = api.saveHistoryPreferences(pdfPreference, hours); retentionHours = saved.optInt("retentionHours", hours) }.onFailure { error = it.message } }
                    }, onOpen = { page = it })
                }
            }
        }
    }
}

@Composable
private fun HomePage(info: String, devices: JSONArray, busy: Boolean, onAction: (String) -> Unit, onDevice: (String) -> Unit, onHistory: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("FAMILY SAFETY • PARENT MODE", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Your family at a glance", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text(info, color = Muted, fontSize = 12.sp)
                }
            }
        }
        item { SectionTitle("Monitored Children", "${devices.length()} linked") }
        if (devices.length() == 0) item {
            EmptyCard("No Kids device linked", "Add a child profile, then link their Kids device using the OTP generated on that device.", "Link Device") { onAction("Link Device") }
        }
        items((0 until devices.length()).toList()) { i ->
            val d = devices.optJSONObject(i) ?: JSONObject()
            ChildCard(d, onOpen = { onDevice(d.optString("kidsDeviceId")) }, onHistory = { onHistory(d.optString("kidsDeviceId")) })
        }
        item { SectionTitle("Parent Controls", "Instant tools") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Add Child", "Profile & age", Icons.Default.PersonAdd, Modifier.weight(1f)) { onAction("Add Child") }
                ActionTile("Link Device", "OTP pairing", Icons.Default.QrCodeScanner, Modifier.weight(1f)) { onAction("Link Device") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionTile("Check-In", "Request status", Icons.Default.LocationOn, Modifier.weight(1f)) { onAction("Check-In") }
                ActionTile("Downtime", "Bedtime & focus", Icons.Default.DarkMode, Modifier.weight(1f)) { onAction("Downtime") }
            }
        }
        item { StatusCard("Background sync", "Server connection status is shown above. Device telemetry appears only after a Kids device is linked and sends data.") }
    }
}

@Composable
private fun ChildCard(d: JSONObject, onOpen: () -> Unit, onHistory: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color(0xFFE0E7FF), shape = RoundedCornerShape(50)) { Icon(Icons.Default.ChildCare, null, tint = Navy, modifier = Modifier.padding(12.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Kids device", fontWeight = FontWeight.Bold, color = Ink)
                    Text(d.optString("kidsDeviceId"), fontSize = 10.sp, color = Muted)
                }
                AssistChip(onClick = onOpen, label = { Text(d.optString("bindingStatus", "UNKNOWN")) })
            }
            Text("Plan: ${d.optString("planName")} • Permit expires: ${d.optString("expiresAt")}", fontSize = 12.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Device details") }
                Button(onClick = onHistory, modifier = Modifier.weight(1f)) { Text("History") }
            }
        }
    }
}

@Composable
private fun DevicesPage(devices: JSONArray, onAction: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Devices & Diagnostics", "${devices.length()} linked") }
        item { ActionTile("Pair a Kids device", "OTP + plan + verified payment", Icons.Default.AddLink, Modifier.fillMaxWidth()) { onAction("Pair Device") } }
        items((0 until devices.length()).toList()) { i ->
            val d = devices.optJSONObject(i) ?: JSONObject()
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text(d.optString("kidsDeviceId"), color = Ink, fontWeight = FontWeight.Bold)
                    Text("${d.optString("planName")} • ${d.optString("bindingStatus")}", color = Muted, fontSize = 12.sp)
                    Text("Permit: ${d.optString("permitCode")}", color = Muted, fontSize = 12.sp)
                    Text("Expires: ${d.optString("expiresAt")}", color = Muted, fontSize = 12.sp)
                    TextButton(onClick = { onAction("Device Diagnostics") }) { Text("Run Diagnostics") }
                }
            }
        }
        item { StatusCard("Diagnostics", "Live ping, battery, location freshness and unlink/revoke actions require Kids telemetry and device-management endpoints. No sample values are shown.") }
    }
}

@Composable
private fun ActivityPage(devices: JSONArray, onOpen: (String) -> Unit, onReports: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Activity & Screen Time", "History") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Today", "7 Days", "30 Days").forEach { FilterChip(selected = it == "Today", onClick = {}, label = { Text(it) }) }
        } }
        if (devices.length() == 0) item { EmptyCard("No activity available", "Link a Kids device and allow the relevant transparent permissions before activity appears.", "Link Device") { onReports() } }
        items((0 until devices.length()).toList()) { i ->
            val d = devices.optJSONObject(i) ?: JSONObject()
            ActionTile("Open device history", d.optString("kidsDeviceId"), Icons.Default.History, Modifier.fillMaxWidth()) { onOpen(d.optString("kidsDeviceId")) }
        }
        item { ActionTile("Reports & Exports", "PDF / CSV", Icons.Default.PictureAsPdf, Modifier.fillMaxWidth(), onReports) }
        item { StatusCard("Activity data", "Screen-time charts and app usage are not fabricated. They appear after the Kids app submits permitted telemetry.") }
    }
}

@Composable
private fun AlertsPage(onOpen: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Alerts & Emergency Centre", "Live alerts") }
        item { EmptyCard("No alert feed connected", "SOS, geofence and check-in events will appear here when their backend event pipeline is enabled.", "Alert settings") { onOpen("Notification Settings") } }
        item { ActionTile("Location & Safe Places", "Map, routes and geofences", Icons.Default.Map, Modifier.fillMaxWidth()) { onOpen("Location & Safe Places") } }
        item { ActionTile("Quiet Hours & Sounds", "Notification behavior", Icons.Default.NotificationsActive, Modifier.fillMaxWidth()) { onOpen("Notification Settings") } }
    }
}

@Composable
private fun SettingsPage(search: String, onSearch: (String) -> Unit, pdf: Boolean, retentionHours: Int, onPdf: (Boolean) -> Unit, onRetention: (Int) -> Unit, onOpen: (String) -> Unit) {
    val groups = listOf(
        "Account & Security" to listOf("Parent profile", "Active Sessions", "Security & Privacy"),
        "Family & Guardians" to listOf("Managed Child Profiles", "Guardian Permissions", "Family Timezone"),
        "Safety Policies & Routines" to listOf("Bedtime & School Routines", "Safe Geofences & Places", "Emergency SOS Escalation"),
        "Privacy, Data & Compliance" to listOf("Data Retention Schedule", "Save History as PDF Before Auto-Delete", "AI Insights Opt-Out", "Export Family Data", "Delete Account & Wipe Vault"),
        "Appearance & Language" to listOf("Language / भाषा", "App Theme"),
        "Support & Legal" to listOf("WhatsApp Fast Support", "Android Permission Health", "Check Updates")
    )
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(search, onSearch, Modifier.fillMaxWidth(), label = { Text("Search settings, routines, privacy…") }, singleLine = true) }
        groups.forEach { (group, options) ->
            val visible = options.filter { it.contains(search, true) || group.contains(search, true) }
            if (visible.isNotEmpty()) {
                item { Text(group.uppercase(), color = Teal, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                items(visible) { option ->
                    if (option == "Data Retention Schedule") {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Unviewed history retention", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text("Keep data while Parent is offline. First successful view starts the separate 24-hour deletion timer.", color = Muted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = retentionHours == 24, onClick = { onRetention(24) }, label = { Text("24 hours") })
                                    FilterChip(selected = retentionHours == 168, onClick = { onRetention(168) }, label = { Text("7 days") })
                                }
                            }
                        }
                    } else if (option == "Save History as PDF Before Auto-Delete") {
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("📄 Save History as PDF Before Auto-Delete", color = Ink, fontWeight = FontWeight.SemiBold)
                                    Text("Save a local report when viewed history is opened.", color = Muted, fontSize = 11.sp)
                                }
                                Switch(pdf, onPdf)
                            }
                        }
                    } else {
                        Card(Modifier.clickable { onOpen(option) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(option, Modifier.weight(1f), color = Ink)
                                Icon(Icons.Default.ChevronRight, null, tint = Muted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailPage(page: String, onBack: () -> Unit, api: ParentApi, selectedDevice: String, pdfPreference: Boolean, onPdfPreference: (Boolean) -> Unit) {
    var activePage by remember(page) { mutableStateOf(page) }
    var otp by remember { mutableStateOf("") }
    var pairingSessionId by remember { mutableStateOf("") }
    var planList by remember { mutableStateOf(JSONArray()) }
    var selectedPlanId by remember { mutableStateOf("") }
    var createdOrder by remember { mutableStateOf<JSONObject?>(null) }
    var records by remember { mutableStateOf(JSONArray()) }\n    var permissionRows by remember { mutableStateOf(JSONArray()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("This section is ready for its authenticated service integration.") }
    val scope = rememberCoroutineScope()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text(" Back") } }
        item { Text(activePage, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink) }
        when (activePage) {
            "Pair Device" -> {
                item { Text("Enter the 6-digit OTP shown in the Kids app. Plans are shown only after server verification.", color = Muted) }
                item { OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(), label = { Text("Kids OTP") }, singleLine = true) }
                item { Button(onClick = { scope.launch { loading = true; runCatching { val result = api.validatePairingOtp(otp); pairingSessionId = result.getString("pairingSessionId"); planList = api.plans(pairingSessionId); activePage = "Select Plan"; message = "Code verified. Select a plan for this Kids installation." }.onFailure { message = it.message ?: "Pairing verification failed" }; loading = false } }, enabled = otp.length == 6 && !loading) { Text(if (loading) "Verifying…" else "Verify OTP") } }
            }
            "Select Plan" -> {
                item { Text("Choose one plan for this Kids installation. The server controls price, duration and eligibility.", color = Muted) }
                items((0 until planList.length()).toList()) { i ->
                    val plan = planList.optJSONObject(i) ?: JSONObject()
                    val id = plan.optString("id")
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().clickable { selectedPlanId = id }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedPlanId == id, onClick = { selectedPlanId = id })
                            Column(Modifier.weight(1f)) {
                                Text(plan.optString("name"), color = Ink, fontWeight = FontWeight.Bold)
                                Text("${plan.optString("currency")} ${plan.optLong("amountPaise") / 100.0} • ${plan.optString("durationValue")} ${plan.optString("durationUnit")}", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                item { Button(enabled = selectedPlanId.isNotBlank() && !loading, onClick = { scope.launch { loading = true; runCatching { createdOrder = api.createPaymentOrder(selectedPlanId, pairingSessionId); message = "Order created. Payment capture and Permit activation are not yet integrated in this APK."; activePage = "Payment Order" }.onFailure { message = it.message ?: "Unable to create payment order" }; loading = false } }) { Text(if (loading) "Creating order…" else "Continue to payment") } }
            }
            "Payment Order" -> {
                item { Text("Gateway order created; no payment is captured or Permit activated yet.", color = Muted) }
                item { createdOrder?.let { order -> StatusCard("Payment pending", "Order ID: ${order.optString("orderId")}\nGateway order: ${order.optString("gatewayOrderId")}\nAmount: ${order.optLong("amountPaise") / 100.0} ${order.optString("currency")}\nKey ID: ${order.optString("keyId")}") } }
            }
            "Android Permission Health" -> {\n                item { Button(onClick = { scope.launch { loading = true; runCatching { permissionRows = api.permissionStatus(selectedDevice); message = "Permission snapshot refreshed from Kids device." }.onFailure { message = it.message ?: "Permission status unavailable" }; loading = false } }, enabled = selectedDevice.isNotBlank() && !loading) { Text(if (loading) "Loading…" else "Refresh permission status") } }\n                items((0 until permissionRows.length()).toList()) { i ->\n                    val row = permissionRows.optJSONObject(i) ?: JSONObject()\n                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {\n                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {\n                            Column(Modifier.weight(1f)) { Text(row.optString("key"), color = Ink, fontWeight = FontWeight.SemiBold); Text(row.optString("detail"), color = Muted, fontSize = 11.sp); Text("Checked: ${row.optString("lastCheckedAt")}", color = Muted, fontSize = 10.sp) }\n                            AssistChip(onClick = {}, label = { Text(if (row.optBoolean("granted")) "Granted" else "Not granted") })\n                        }\n                    }\n                }\n                if (permissionRows.length() == 0) item { StatusCard("No Kids permission report", "The Kids app must request permissions on its own device and submit a status snapshot. Parent cannot silently grant Android permissions.") }\n            }\n            "History" -> {
                item { Button(onClick = {
                    scope.launch { loading = true; runCatching { records = api.history(selectedDevice); message = "History loaded from server." }.onFailure { message = it.message ?: "History unavailable" }; loading = false }
                }) { Text(if (loading) "Loading…" else "Load History") } }
                item { Text("Data will auto-delete 24 hours after first view. Downloaded copies do not stop cloud deletion.", color = Muted, fontSize = 12.sp) }
                items((0 until records.length()).toList()) { i ->
                    val r = records.optJSONObject(i) ?: JSONObject()
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(r.optString("recordType"), fontWeight = FontWeight.Bold, color = Ink)
                            Text("Delete at: ${r.optString("deleteAt")}", fontSize = 11.sp, color = Muted)
                            Text(r.optString("payloadJson"), fontSize = 11.sp, color = Muted)
                            TextButton(onClick = { scope.launch { runCatching { api.deleteHistory(r.optString("id")); message = "Deleted from cloud." }.onFailure { message = it.message ?: "Delete failed" } } }) { Text("Delete") }
                        }
                    }
                }
            }
            else -> {
                item { StatusCard("Not yet connected", "The approved design is preserved as the target. This control must not show success until its backend/device integration is implemented.") }
                item { TextButton(onClick = { message = "Feature status checked. No live endpoint is connected for this action yet." }) { Text("Check feature status") } }
            }
        }
        item { Text(message, color = Muted, fontSize = 12.sp) }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Text(trailing, color = Teal, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun StatusCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color(0xFFE0E7FF), shape = RoundedCornerShape(12.dp)) {
                Icon(icon, null, tint = Navy, modifier = Modifier.padding(10.dp).size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = Muted, fontSize = 10.sp)
            }
        }
    }
}
