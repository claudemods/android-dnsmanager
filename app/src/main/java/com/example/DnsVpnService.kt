package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

class DnsVpnService : VpnService() {
    companion object {
        val isRunning = MutableStateFlow(false)
        private const val CHANNEL_ID = "dns_vpn_channel_v2"
        private const val NOTIFICATION_ID = 101
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "STOP") {
            stopVpn()
            return Service.START_NOT_STICKY
        }

        val prefs = getSharedPreferences("dns_prefs", MODE_PRIVATE)
        val name = intent?.getStringExtra("name") ?: prefs.getString("name", "DNS Protection") ?: "DNS Protection"
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
            try {
                vpnInterface?.close()
            } catch (e: Exception) {
                Log.e("DnsVpnService", "Error closing existing VPN interface", e)
            }
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

            vpnInterface = builder.establish()
            if (vpnInterface != null) {
                isRunning.value = true
                Log.d("DnsVpnService", "VPN established for $name")
                showNotification(name)
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

    private fun showNotification(providerName: String) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS Protection Active")
            .setContentText("Connected to $providerName")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DNS Connection Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Displays active DNS connection status in notification bar"
                setShowBadge(true)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
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
        stopForeground(STOP_FOREGROUND_REMOVE)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }
}
