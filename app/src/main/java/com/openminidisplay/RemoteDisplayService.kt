package com.openminidisplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
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
    private lateinit var screenManager: ScreenManager

    private var serverSocket: ServerSocket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var serviceWakeLock: PowerManager.WakeLock? = null
    private var serverJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lowPowerJob: Job? = null
    private var clientSocket: Socket? = null

    private val lastHeartbeatAt = AtomicLong(0L)

    override fun onCreate() {
        super.onCreate()
        screenManager = ScreenManager(applicationContext)
        acquireServiceWakeLock()
        acquireWifiLock()
        startForeground(NOTIFICATION_ID, buildNotification(ConnectionState.DISCONNECTED))
        startListener()
        startHeartbeatMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_USER_ACTIVITY) {
            handleUserActivity()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        heartbeatJob?.cancel()
        lowPowerJob?.cancel()
        serverJob?.cancel()
        closeClientSocket()
        closeServerSocket()
        releaseWifiLock()
        releaseServiceWakeLock()
        screenManager.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startListener() {
        serverJob = serviceScope.launch {
            try {
                serverSocket = ServerSocket(LISTEN_PORT).apply {
                    reuseAddress = true
                    soTimeout = SOCKET_ACCEPT_TIMEOUT_MS.toInt()
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
                Log.e(TAG, "Failed to start listener", exception)
            }
        }
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
                delay(HEARTBEAT_CHECK_INTERVAL_MS)
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
        val current = ConnectionStateRepository.state.value
        if (current == state) return

        ConnectionStateRepository.updateState(state)
        updateNotification(state)

        when (state) {
            ConnectionState.CONNECTED -> {
                cancelLowPowerMode()
                screenManager.onConnected()
            }
            ConnectionState.DISCONNECTED -> {
                lastHeartbeatAt.set(0L)
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
        if (ConnectionStateRepository.state.value != ConnectionState.DISCONNECTED) return

        Log.i(TAG, "User activity detected; restoring brightness and resetting low-power timer")
        screenManager.cancelDimming()
        screenManager.restoreBrightnessLevel()
        screenManager.navigateToDashboard()
        scheduleLowPowerMode()
    }

    private fun cancelLowPowerMode() {
        lowPowerJob?.cancel()
        lowPowerJob = null
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

        val statusText = when (state) {
            ConnectionState.CONNECTED -> getString(R.string.status_connected)
            ConnectionState.DISCONNECTED -> getString(R.string.status_waiting)
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
        private const val SOCKET_ACCEPT_TIMEOUT_MS = 2_000L
        private const val SOCKET_READ_TIMEOUT_MS = 2_000L
        private const val RETRY_DELAY_MS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, RemoteDisplayService::class.java)
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
