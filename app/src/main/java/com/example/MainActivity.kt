package com.example

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow

data class DnsProvider(
    val name: String,
    val ipv4Primary: String,
    val ipv4Secondary: String,
    val ipv6Primary: String,
    val ipv6Secondary: String,
    val https: String? = null,
    val tls: String? = null
)

val Providers = listOf(
    DnsProvider(
        name = "☁️ Cloudflare DNS",
        ipv4Primary = "1.1.1.1",
        ipv4Secondary = "1.0.0.1",
        ipv6Primary = "2606:4700:4700::1111",
        ipv6Secondary = "2606:4700:4700::1001"
    ),
    DnsProvider(
        name = "🔍 Google Public DNS",
        ipv4Primary = "8.8.8.8",
        ipv4Secondary = "8.8.4.4",
        ipv6Primary = "2001:4860:4860::8888",
        ipv6Secondary = "2001:4860:4860::8844"
    ),
    DnsProvider(
        name = "🏠 OpenDNS",
        ipv4Primary = "208.67.222.222",
        ipv4Secondary = "208.67.220.220",
        ipv6Primary = "2620:119:35::35",
        ipv6Secondary = "2620:119:53::53"
    ),
    DnsProvider(
        name = "🛡️ Quad9 DNS",
        ipv4Primary = "9.9.9.11",
        ipv4Secondary = "149.112.112.11",
        ipv6Primary = "2620:fe::11",
        ipv6Secondary = "2620:fe::fe:11"
    ),
    DnsProvider(
        name = "🚫 AdGuard DNS",
        ipv4Primary = "94.140.14.14",
        ipv4Secondary = "94.140.15.15",
        ipv6Primary = "2a10:50c0::ad1:ff",
        ipv6Secondary = "2a10:50c0::ad2:ff"
    )
)

class MainActivity : ComponentActivity() {

    private val selectedProvider = MutableStateFlow(Providers[0])
    private val showVpnSettingsDialog = MutableStateFlow(false)
    
    private var pendingProvider: DnsProvider? = null

    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingProvider?.let { startVpn(it) }
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val prefs = getSharedPreferences("dns_prefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("selected_provider_name", null) ?: prefs.getString("name", null)
        if (savedName != null) {
            val found = Providers.find { it.name == savedName }
            if (found != null) {
                selectedProvider.value = found
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    val connected by DnsVpnService.isRunning.collectAsState()
                    val currentProvider by selectedProvider.collectAsState()
                    val showDialog by showVpnSettingsDialog.collectAsState()

                    DnsChangerScreen(
                        modifier = Modifier.padding(innerPadding),
                        providers = Providers,
                        selectedProvider = currentProvider,
                        isConnected = connected,
                        onProviderSelected = { provider ->
                            selectedProvider.value = provider
                            val p = getSharedPreferences("dns_prefs", Context.MODE_PRIVATE)
                            p.edit().putString("selected_provider_name", provider.name).apply()
                        },
                        onToggleConnection = {
                            if (connected) {
                                stopVpn()
                            } else {
                                checkVpnPermissionAndStart(currentProvider)
                            }
                        },
                        showVpnSettingsDialog = showDialog,
                        onDismissDialog = { showVpnSettingsDialog.value = false },
                        onOpenSettings = {
                            showVpnSettingsDialog.value = false
                            try {
                                startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
                            } catch (e: Exception) {
                                // Fallback if intent fails
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkVpnPermissionAndStart(provider: DnsProvider) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val intent = VpnService.prepare(this)
        if (intent != null) {
            pendingProvider = provider
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpn(provider)
        }
    }

    private fun startVpn(provider: DnsProvider) {
        val prefs = getSharedPreferences("dns_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("selected_provider_name", provider.name)
            putString("name", provider.name)
            putString("ipv4Primary", provider.ipv4Primary)
            putString("ipv4Secondary", provider.ipv4Secondary)
            putString("ipv6Primary", provider.ipv6Primary)
            putString("ipv6Secondary", provider.ipv6Secondary)
        }.apply()

        val intent = Intent(this, DnsVpnService::class.java).apply {
            putExtra("name", provider.name)
            putExtra("ipv4Primary", provider.ipv4Primary)
            putExtra("ipv4Secondary", provider.ipv4Secondary)
            putExtra("ipv6Primary", provider.ipv6Primary)
            putExtra("ipv6Secondary", provider.ipv6Secondary)
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
        showVpnSettingsDialog.value = true
    }

    private fun stopVpn() {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        checkVpnActive()
    }

    private fun checkVpnActive() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        DnsVpnService.isRunning.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }
}

@Composable
fun DnsChangerScreen(
    modifier: Modifier = Modifier,
    providers: List<DnsProvider>,
    selectedProvider: DnsProvider,
    isConnected: Boolean,
    onProviderSelected: (DnsProvider) -> Unit,
    onToggleConnection: () -> Unit,
    showVpnSettingsDialog: Boolean,
    onDismissDialog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp)
    ) {
        Text(
            text = "DNS Changer",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = "Select a DNS provider to secure your connection.",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            providers.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isSelected = provider == selectedProvider,
                    onClick = {
                        if (!isConnected) {
                            onProviderSelected(provider)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onToggleConnection,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (isConnected) "Disconnect" else "Connect",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "v1.0 30-07-2026 by claudemods",
            style = MaterialTheme.typography.labelSmall.copy(
                color = androidx.compose.ui.graphics.Color.Red,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .align(Alignment.CenterHorizontally)
        )

        if (showVpnSettingsDialog) {
            AlertDialog(
                onDismissRequest = onDismissDialog,
                title = { Text("Always On VPN") },
                text = { Text("For the best experience, you can set this to 'Always On' in settings.\n\n⚠️ IMPORTANT: Do NOT enable 'Block connections without VPN'. This is a DNS-only VPN, and blocking connections will disable your internet!") },
                confirmButton = {
                    TextButton(onClick = onOpenSettings) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDialog) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProviderCard(
    provider: DnsProvider,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isSelected) 0.dp else 4.dp,
        shadowElevation = if (isSelected) 0.dp else 4.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                DnsInfoRow(label = "IPv4", value = "${provider.ipv4Primary}, ${provider.ipv4Secondary}")
                Spacer(modifier = Modifier.height(2.dp))
                DnsInfoRow(label = "IPv6", value = "${provider.ipv6Primary}, ${provider.ipv6Secondary}")
                
                if (provider.tls != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    DnsInfoRow(label = "TLS", value = provider.tls)
                }
                if (provider.https != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    DnsInfoRow(label = "HTTPS", value = provider.https)
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@Composable
fun DnsInfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            ),
            modifier = Modifier.width(36.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )
        )
    }
}
