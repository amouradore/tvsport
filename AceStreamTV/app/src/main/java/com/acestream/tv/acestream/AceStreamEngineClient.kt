package com.acestream.tv.acestream

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import org.acestream.engine.service.v0.IAceStreamManagerService
import kotlin.coroutines.resume

class AceStreamEngineClient(private val context: Context) {

    private var service: IAceStreamManagerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d("AceStreamClient", "Service connected")
            service = IAceStreamManagerService.Stub.asInterface(binder)
            try {
                service?.registerClient("AceStreamTV")
                service?.startEngine()
            } catch (e: Exception) {
                Log.e("AceStreamClient", "Error initializing engine: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("AceStreamClient", "Service disconnected")
            service = null
            isBound = false
        }
    }

    suspend fun bind(): Boolean = suspendCancellableCoroutine { continuation ->
        if (isBound) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }

        val intent = Intent("org.acestream.engine.service.v0.AceStreamManagerService")
        intent.setPackage("org.acestream.node")
        
        try {
            val result = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (result) {
                isBound = true
                Log.d("AceStreamClient", "Bind request successful")
                continuation.resume(true)
            } else {
                Log.e("AceStreamClient", "Bind request failed")
                continuation.resume(false)
            }
        } catch (e: Exception) {
            Log.e("AceStreamClient", "Bind exception: ${e.message}")
            continuation.resume(false)
        }
    }

    fun unbind() {
        if (isBound) {
            try {
                service?.unregisterClient("AceStreamTV")
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e("AceStreamClient", "Unbind error: ${e.message}")
            }
            isBound = false
            service = null
        }
    }
}
