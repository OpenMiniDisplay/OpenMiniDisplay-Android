package com.openminidisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.openminidisplay.display.protocol.DisplayCommandHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicLong

class RemoteDisplayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val screenManager get() = OpenMiniDisplayApp.screenManagerOf(this)

    private var serverSocket: ServerSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var serverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lowPowerJob: Job? = null
    private var clientSocket: Socket? = null

    private val lastHeartbeatAt = AtomicLong(0L)

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            handleBatteryChanged(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        RuntimeState.setPluggedIn(PowerState.isPluggedIn(this))
        if (batteryIntent != null) {
            RuntimeState.setBatteryLevel(PowerState.batteryLevelPercent(batteryIntent))
        }
        registerPowerReceiver()
        if (RuntimeState.isPluggedIn.value) {
            ChargeLimitManager.onPowerConnected(this)
        }
        acquireServiceWakeLock()
        acquireWifiLock()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.DISCONNECTED))
        startListener()
        startHeartbeatMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_USER_ACTIVITY -> handleUserActivity()
            ACTION_BATTERY_DEEP_IDLE -> enterBatteryDeepIdle()
            ACTION_EXIT_BATTERY_DEEP_IDLE -> exitBatteryDeepIdle()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterPowerReceiver()
        heartbeatJob?.cancel()
        lowPowerJob?.cancel()
        serverJob?.cancel()
        closeClientSocket()
        closeServerSocket()
        releaseWifiLock()
        releaseServiceWakeLock()
        screenManager.releaseWakeLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListener() {
        if (RuntimeState.batteryDeepIdle.value) return

        serverJob?.cancel()
        closeClientSocket()
        closeServerSocket()

        serverJob = serviceScope.launch {
            try {
                val timeoutMs = currentAcceptTimeoutMs().toInt()
                serverSocket = ServerSocket(LISTEN_PORT).apply {
                    reuseAddress = true
                    soTimeout = timeoutMs
                }
                Log.i(TAG, "Listening on port $LISTEN_PORT")

                while (isActive) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        handleClient(socket)
                    } catch (_: SocketTimeoutException) {
                        // Expected when no client is waiting; keep listening.
                    } catch (exception: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Accept loop error", exception)
                            delay(RETRY_DELAY_MS)
                        }
                    }
                }
            } catch (exception: Exception) {
                if (isActive) {
                    Log.e(TAG, "Failed to start listener", exception)
                }
            }
        }
    }

    private fun stopListener() {
        serverJob?.cancel()
        serverJob = null
        closeClientSocket()
        closeServerSocket()
        Log.i(TAG, "Stopped listening on port $LISTEN_PORT")
    }

    private fun handleClient(socket: Socket) {
        closeClientSocket()
        clientSocket = socket

        serviceScope.launch {
            try {
                socket.soTimeout = SOCKET_READ_TIMEOUT_MS.toInt()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) continue

                    when {
                        isHandshake(line) -> {
                            recordHeartbeat()
                            setConnectionState(ConnectionState.CONNECTED)
                        }
                        isHeartbeat(line) -> recordHeartbeat()
                        DisplayCommandHandler.handle(line) -> recordHeartbeat()
                    }
                }
            } catch (_: SocketTimeoutException) {
                // Read timeout is handled by the heartbeat monitor.
            } catch (exception: Exception) {
                Log.w(TAG, "Client session ended", exception)
            } finally {
                closeClientSocket()
                scheduleDisconnectedIfStale()
            }
        }
    }

    private fun startHeartbeatMonitor() {
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                val intervalMs = when {
                    RuntimeState.connectionState.value != ConnectionState.CONNECTED -> HEARTBEAT_IDLE_INTERVAL_MS
                    RuntimeState.batteryDeepIdle.value -> HEARTBEAT_IDLE_INTERVAL_MS
                    else -> HEARTBEAT_CHECK_INTERVAL_MS
                }
                delay(intervalMs)
                if (RuntimeState.connectionState.value != ConnectionState.CONNECTED) continue

                val elapsed = System.currentTimeMillis() - lastHeartbeatAt.get()
                if (lastHeartbeatAt.get() > 0L && elapsed > HEARTBEAT_TIMEOUT_MS) {
                    setConnectionState(ConnectionState.DISCONNECTED)
                }
            }
        }
    }

    private fun recordHeartbeat() {
        lastHeartbeatAt.set(System.currentTimeMillis())
    }

    private fun scheduleDisconnectedIfStale() {
        serviceScope.launch {
            delay(HEARTBEAT_TIMEOUT_MS)
            val elapsed = System.currentTimeMillis() - lastHeartbeatAt.get()
            if (elapsed > HEARTBEAT_TIMEOUT_MS) {
                setConnectionState(ConnectionState.DISCONNECTED)
            }
        }
    }

    private fun setConnectionState(state: ConnectionState) {
        val current = RuntimeState.connectionState.value
        if (current == state) return

        RuntimeState.setConnectionState(state)
        updateNotification(state)

        when (state) {
            ConnectionState.CONNECTED -> {
                cancelLowPowerMode()
                wakeFromBatteryDeepIdle()
                screenManager.onConnected()
            }
            ConnectionState.DISCONNECTED -> {
                lastHeartbeatAt.set(0L)
                screenManager.onDisconnected()
                if (!RuntimeState.isPluggedIn.value) {
                    releaseServiceWakeLock()
                    releaseWifiLock()
                    updateListenerTimeout()
                }
                scheduleLowPowerMode()
            }
        }
    }

    private fun scheduleLowPowerMode() {
        lowPowerJob?.cancel()
        lowPowerJob = serviceScope.launch {
            Log.i(TAG, "Connection lost; entering low-power mode in ${LOW_POWER_DELAY_MS}ms")
            delay(LOW_POWER_DELAY_MS)
            screenManager.beginLowPowerTransition()
        }
    }

    private fun handleUserActivity() {
        if (RuntimeState.connectionState.value != ConnectionState.DISCONNECTED) return

        Log.i(TAG, "User activity detected; restoring brightness and resetting low-power timer")
        wakeFromBatteryDeepIdle()
        screenManager.cancelDimming()
        screenManager.restoreBrightnessLevel()
        screenManager.navigateToDashboard()
        scheduleLowPowerMode()
    }

    private fun cancelLowPowerMode() {
        lowPowerJob?.cancel()
        lowPowerJob = null
    }

    private fun handleBatteryChanged(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val wasPlugged = RuntimeState.isPluggedIn.value
        RuntimeState.setPluggedIn(plugged)
        RuntimeState.setBatteryLevel(PowerState.batteryLevelPercent(intent))

        when {
            plugged && !wasPlugged -> {
                wakeFromBatteryDeepIdle()
                ChargeLimitManager.onPowerConnected(this)
            }
            !plugged && wasPlugged -> ChargeLimitManager.onPowerDisconnected(this)
        }
    }

    private fun enterBatteryDeepIdle() {
        if (RuntimeState.batteryDeepIdle.value) return
        if (RuntimeState.isPluggedIn.value) return
        if (RuntimeState.connectionState.value == ConnectionState.CONNECTED) return

        Log.i(TAG, "Entering battery deep idle: stopping listener and releasing wake locks")
        RuntimeState.setBatteryDeepIdle(true)
        stopListener()
        releaseServiceWakeLock()
        releaseWifiLock()
        updateNotification(RuntimeState.connectionState.value)
    }

    private fun wakeFromBatteryDeepIdle() {
        val wasDeepIdle = RuntimeState.batteryDeepIdle.value
        if (wasDeepIdle) {
            RuntimeState.setBatteryDeepIdle(false)
            Log.i(TAG, "Woke from battery deep idle: listener restarted")
        }

        acquireServiceWakeLock()
        acquireWifiLock()

        if (wasDeepIdle || !isListenerRunning()) {
            startListener()
        } else {
            updateListenerTimeout()
        }
        updateNotification(RuntimeState.connectionState.value)

        if (wasDeepIdle && RuntimeState.connectionState.value == ConnectionState.DISCONNECTED) {
            scheduleLowPowerMode()
        }
    }

    private fun isListenerRunning(): Boolean {
        val socket = serverSocket
        return serverJob?.isActive == true && socket != null && !socket.isClosed
    }

    private fun exitBatteryDeepIdle() {
        wakeFromBatteryDeepIdle()
    }

    private fun updateListenerTimeout() {
        val timeoutMs = currentAcceptTimeoutMs()
        try {
            serverSocket?.soTimeout = timeoutMs.toInt()
        } catch (exception: Exception) {
            Log.w(TAG, "Failed to update listener timeout", exception)
        }
    }

    private fun currentAcceptTimeoutMs(): Long {
        return if (
            !RuntimeState.isPluggedIn.value &&
            RuntimeState.connectionState.value == ConnectionState.DISCONNECTED
        ) {
            BATTERY_IDLE_ACCEPT_TIMEOUT_MS
        } else {
            SOCKET_ACCEPT_TIMEOUT_MS
        }
    }

    private fun registerPowerReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                this,
                powerReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    private fun unregisterPowerReceiver() {
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: IllegalArgumentException) {
        }
    }

    private fun isHandshake(message: String): Boolean {
        return message.equals(HANDSHAKE_TOKEN, ignoreCase = true) ||
            message.startsWith("CONNECT", ignoreCase = true)
    }

    private fun isHeartbeat(message: String): Boolean {
        return message.equals(HEARTBEAT_TOKEN, ignoreCase = true) ||
            message.startsWith("PING", ignoreCase = true)
    }

    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wifiLock = wifiManager.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "$TAG:WifiLock",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        wifiLock = null
    }

    private fun acquireServiceWakeLock() {
        if (serviceWakeLock?.isHeld == true) return

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        serviceWakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$TAG:ServiceWakeLock",
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseServiceWakeLock() {
        serviceWakeLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
        }
        serviceWakeLock = null
    }

    private fun closeClientSocket() {
        try {
            clientSocket?.close()
        } catch (_: Exception) {
        } finally {
            clientSocket = null
        }
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        } finally {
            serverSocket = null
        }
    }

    private fun buildNotification(state: ConnectionState): Notification {
        createNotificationChannel()

        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val statusText = when {
            RuntimeState.batteryDeepIdle.value -> getString(R.string.status_battery_saver)
            state == ConnectionState.CONNECTED -> getString(R.string.status_connected)
            else -> getString(R.string.status_waiting)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(state: ConnectionState) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "RemoteDisplayService"

        const val LISTEN_PORT = 15180
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "remote_display_service"

        private const val HANDSHAKE_TOKEN = "OPENMINIDISPLAY"
        private const val HEARTBEAT_TOKEN = "PING"

        private const val HEARTBEAT_TIMEOUT_MS = 5_000L
        private const val LOW_POWER_DELAY_MS = 60_000L
        private const val HEARTBEAT_CHECK_INTERVAL_MS = 1_000L
        private const val HEARTBEAT_IDLE_INTERVAL_MS = 30_000L
        private const val SOCKET_ACCEPT_TIMEOUT_MS = 2_000L
        private const val BATTERY_IDLE_ACCEPT_TIMEOUT_MS = 30_000L
        private const val SOCKET_READ_TIMEOUT_MS = 2_000L
        private const val RETRY_DELAY_MS = 1_000L

        const val ACTION_BATTERY_DEEP_IDLE = "com.openminidisplay.action.BATTERY_DEEP_IDLE"
        const val ACTION_EXIT_BATTERY_DEEP_IDLE = "com.openminidisplay.action.EXIT_BATTERY_DEEP_IDLE"

        fun start(context: Context) {
            val intent = Intent(context, RemoteDisplayService::class.java).apply {
                action = ACTION_EXIT_BATTERY_DEEP_IDLE
            }
            context.startForegroundService(intent)
        }

        fun notifyUserActivity(context: Context) {
            val intent = Intent(context, RemoteDisplayService::class.java).apply {
                action = ACTION_USER_ACTIVITY
            }
            context.startService(intent)
        }

        const val ACTION_USER_ACTIVITY = "com.openminidisplay.action.USER_ACTIVITY"
    }
}
