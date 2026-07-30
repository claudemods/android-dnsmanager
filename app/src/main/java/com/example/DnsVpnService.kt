package com.example

import android.app.Service
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow

class DnsVpnService : VpnService() {
    companion object {
        val isRunning = MutableStateFlow(false)
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return Service.START_NOT_STICKY
        }

        val prefs = getSharedPreferences("dns_prefs", MODE_PRIVATE)
        val name = intent?.getStringExtra("name") ?: prefs.getString("name", "DNS Changer") ?: "DNS Changer"
        val ipv4Primary = intent?.getStringExtra("ipv4Primary") ?: prefs.getString("ipv4Primary", null)
        val ipv4Secondary = intent?.getStringExtra("ipv4Secondary") ?: prefs.getString("ipv4Secondary", null)
        val ipv6Primary = intent?.getStringExtra("ipv6Primary") ?: prefs.getString("ipv6Primary", null)
        val ipv6Secondary = intent?.getStringExtra("ipv6Secondary") ?: prefs.getString("ipv6Secondary", null)

        if (ipv4Primary == null) {
            stopVpn()
            return Service.START_NOT_STICKY
        }

        startVpn(name, ipv4Primary, ipv4Secondary, ipv6Primary, ipv6Secondary)
        return Service.START_STICKY
    }

    private fun startVpn(
        name: String,
        ipv4Primary: String,
        ipv4Secondary: String?,
        ipv6Primary: String?,
        ipv6Secondary: String?
    ) {
        if (vpnInterface != null) {
            vpnInterface?.close()
            vpnInterface = null
        }

        try {
            val builder = Builder()
                .setSession(name)
                .addAddress("10.0.0.2", 32) // Dummy address required by Android
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                .addRoute("10.0.0.2", 32) // Dummy route to satisfy Always-On VPN requirements
                .addRoute("fd00:1:fd00:1:fd00:1:fd00:1", 128)

            builder.addDnsServer(ipv4Primary)
            if (ipv4Secondary != null) builder.addDnsServer(ipv4Secondary)
            if (ipv6Primary != null) builder.addDnsServer(ipv6Primary)
            if (ipv6Secondary != null) builder.addDnsServer(ipv6Secondary)

            // Do not add broad routes. By omitting broad routes, we only intercept DNS queries.

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                isRunning.value = true
                Log.d("DnsVpnService", "VPN established for $name")
            } else {
                isRunning.value = false
                Log.e("DnsVpnService", "VPN establish returned null.")
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error starting VPN", e)
            isRunning.value = false
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("DnsVpnService", "Error closing VPN interface", e)
        }
        isRunning.value = false
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
