package io.repobor.autoglm.accessibility

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import io.repobor.autoglm.IShizukuService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Helper class to manage Shizuku UserService connection.
 * This handles binding/unbinding and notifying when the service is ready.
 */
class ShizukuServiceHelper {
    companion object {
        private const val TAG = "ShizukuServiceHelper"
        private var instance: ShizukuServiceHelper? = null

        fun getInstance(): ShizukuServiceHelper {
            if (instance == null) {
                instance = ShizukuServiceHelper()
            }
            return instance!!
        }
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var serviceConnection: ServiceConnection? = null

    /**
     * Bind to Shizuku UserService
     */
    fun bindService() {
        if (serviceConnection != null) {
            Log.d(TAG, "Service already binding/bound")
            return
        }

        Log.d(TAG, "Binding to Shizuku UserService")
        _connectionState.value = ConnectionState.CONNECTING

        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d(TAG, "Shizuku UserService connected")
                if (service != null) {
                    val userService = IShizukuService.Stub.asInterface(service)
                    ShizukuHelper.setUserService(userService)
                    _connectionState.value = ConnectionState.CONNECTED
                } else {
                    Log.e(TAG, "Service binder is null")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d(TAG, "Shizuku UserService disconnected")
                ShizukuHelper.setUserService(null)
                _connectionState.value = ConnectionState.DISCONNECTED
                serviceConnection = null
            }
        }

        ShizukuHelper.bindUserService(serviceConnection!!)
    }

    /**
     * Unbind from Shizuku UserService
     */
    fun unbindService() {
        if (serviceConnection != null) {
            Log.d(TAG, "Unbinding from Shizuku UserService")
            ShizukuHelper.unbindUserService(serviceConnection!!)
            serviceConnection = null
            ShizukuHelper.setUserService(null)
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }
}
