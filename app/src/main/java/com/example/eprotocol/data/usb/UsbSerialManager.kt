package com.example.eprotocol.data.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.example.eprotocol.data.model.ConnectionState
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * USB CDC 串口连接管理器
 *
 * 负责 USB 设备的枚举、权限请求、串口连接管理以及原始字节的收发。
 * 通过 StateFlow 向上层暴露连接状态，通过 SharedFlow 向上层分发接收到的原始字节数据。
 */
class UsbSerialManager {

    companion object {
        private const val TAG = "UsbSerialManager"
        private const val ACTION_USB_PERMISSION = "com.example.eprotocol.USB_PERMISSION"
        private const val BAUD_RATE = 115200
        private const val DATA_BITS = 8
        private const val STOP_BITS = UsbSerialPort.STOPBITS_1
        private const val PARITY = UsbSerialPort.PARITY_NONE
        private const val WRITE_TIMEOUT_MS = 1000
        private const val HEX_PREVIEW_BYTES = 128
    }

    private var usbManager: UsbManager? = null
    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null
    private var ioManager: SerialInputOutputManager? = null
    private var appContext: Context? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _dataFlow = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val dataFlow: SharedFlow<ByteArray> = _dataFlow.asSharedFlow()

    private var permissionReceiver: BroadcastReceiver? = null

    fun connect(context: Context) {
        DiagnosticLog.add(TAG, "connect requested")
        appContext = context.applicationContext
        _connectionState.value = ConnectionState.Connecting
        usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        val manager = usbManager ?: run {
            _connectionState.value = ConnectionState.Error("无法获取 USB 服务")
            return
        }

        val customTable = UsbSerialProber.getDefaultProbeTable()
        val prober = UsbSerialProber(customTable)
        val availableDrivers = prober.findAllDrivers(manager)
        DiagnosticLog.add(TAG, "drivers=${availableDrivers.size}, devices=${manager.deviceList.size}")

        if (availableDrivers.isEmpty()) {
            val deviceList = manager.deviceList
            if (deviceList.isEmpty()) {
                DiagnosticLog.add(TAG, "no USB device found")
                _connectionState.value = ConnectionState.Error("未找到 USB 设备")
                return
            }
            val device = deviceList.values.first()
            DiagnosticLog.add(TAG, "using raw USB device vid=${device.vendorId}, pid=${device.productId}")
            tryConnectDevice(context, manager, device)
            return
        }

        // 优先选择 CdcAcmSerialDriver 类型的驱动
        val driver = availableDrivers.firstOrNull { it is CdcAcmSerialDriver }
            ?: availableDrivers[0]
        val device = driver.device
        DiagnosticLog.add(
            TAG,
            "using driver=${driver.javaClass.simpleName}, vid=${device.vendorId}, pid=${device.productId}, ports=${driver.ports.size}"
        )

        if (manager.hasPermission(device)) {
            openPort(manager, driver.ports[0])
        } else {
            requestPermission(context, manager, device, driver.ports[0])
        }
    }

    private fun tryConnectDevice(context: Context, manager: UsbManager, device: UsbDevice) {
        if (manager.hasPermission(device)) {
            openPortForDevice(manager, device)
        } else {
            requestPermissionForDevice(context, manager, device)
        }
    }

    /**
     * 注销可能残留的旧权限接收器，防止重复注册导致泄漏
     */
    private fun unregisterOldReceiver(context: Context) {
        permissionReceiver?.let { receiver ->
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // 未注册时忽略
            }
            permissionReceiver = null
        }
    }

    private fun requestPermission(
        context: Context,
        manager: UsbManager,
        device: UsbDevice,
        port: UsbSerialPort
    ) {
        unregisterOldReceiver(context)

        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        openPort(manager, port)
                    } else {
                        _connectionState.value = ConnectionState.Error("USB 权限被拒绝")
                    }
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    permissionReceiver = null
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        manager.requestPermission(device, permissionIntent)
    }

    private fun requestPermissionForDevice(
        context: Context,
        manager: UsbManager,
        device: UsbDevice
    ) {
        unregisterOldReceiver(context)

        permissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (ACTION_USB_PERMISSION == intent.action) {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted) {
                        openPortForDevice(manager, device)
                    } else {
                        _connectionState.value = ConnectionState.Error("USB 权限被拒绝")
                    }
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                    permissionReceiver = null
                }
            }
        }

        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(permissionReceiver, filter)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        manager.requestPermission(device, permissionIntent)
    }

    private fun openPortForDevice(manager: UsbManager, device: UsbDevice) {
        try {
            val driver = CdcAcmSerialDriver(device)
            if (driver.ports.isEmpty()) {
                _connectionState.value = ConnectionState.Error("设备无可用串口")
                return
            }
            openPort(manager, driver.ports[0])
        } catch (e: Exception) {
            Log.e(TAG, "手动创建驱动失败", e)
            _connectionState.value = ConnectionState.Error("设备驱动创建失败: ${e.message}")
        }
    }

    private fun openPort(manager: UsbManager, port: UsbSerialPort) {
        try {
            val conn = manager.openDevice(port.driver.device)
            if (conn == null) {
                DiagnosticLog.add(TAG, "openDevice returned null")
                _connectionState.value = ConnectionState.Error("无法打开 USB 设备连接")
                return
            }
            connection = conn
            serialPort = port

            port.open(conn)
            port.setParameters(BAUD_RATE, DATA_BITS, STOP_BITS, PARITY)
            DiagnosticLog.add(TAG, "port opened baud=$BAUD_RATE data=$DATA_BITS stop=$STOP_BITS parity=$PARITY")
            configureControlLines(port)
            purgeBuffers(port)

            startIoManager(port)
            _connectionState.value = ConnectionState.Connected
            Log.i(TAG, "串口已连接: ${port.driver.device.deviceName}")
            DiagnosticLog.add(TAG, "connected deviceName=${port.driver.device.deviceName}")
        } catch (e: IOException) {
            Log.e(TAG, "打开串口失败", e)
            DiagnosticLog.add(TAG, "open failed: ${e.message}")
            _connectionState.value = ConnectionState.Error("打开串口失败: ${e.message}")
            disconnect()
        }
    }

    private fun configureControlLines(port: UsbSerialPort) {
        try {
            port.setDTR(true)
            port.setRTS(true)
            Log.i(TAG, "串口控制线已置位: DTR=true, RTS=true")
            DiagnosticLog.add(TAG, "control lines set DTR=true RTS=true")
        } catch (e: UnsupportedOperationException) {
            Log.i(TAG, "当前串口驱动不支持 DTR/RTS 控制线")
            DiagnosticLog.add(TAG, "control lines unsupported")
        } catch (e: IOException) {
            Log.w(TAG, "设置 DTR/RTS 失败，继续尝试通信", e)
            DiagnosticLog.add(TAG, "control lines failed: ${e.message}")
        }
    }

    private fun purgeBuffers(port: UsbSerialPort) {
        try {
            port.purgeHwBuffers(true, true)
            DiagnosticLog.add(TAG, "hardware buffers purged")
        } catch (e: UnsupportedOperationException) {
            Log.d(TAG, "当前串口驱动不支持清空硬件缓冲")
            DiagnosticLog.add(TAG, "purge buffers unsupported")
        } catch (e: IOException) {
            Log.w(TAG, "清空串口硬件缓冲失败，继续尝试通信", e)
            DiagnosticLog.add(TAG, "purge buffers failed: ${e.message}")
        }
    }

    private fun startIoManager(port: UsbSerialPort) {
        val listener = object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                val message = "RX ${data.size} bytes: ${data.toHexPreview()}"
                Log.d(TAG, message)
                DiagnosticLog.add(TAG, message)
                _dataFlow.tryEmit(data)
            }

            override fun onRunError(e: Exception) {
                Log.e(TAG, "串口 IO 错误", e)
                DiagnosticLog.add(TAG, "IO error: ${e.message}")
                _connectionState.value = ConnectionState.Error("通信错误: ${e.message}")
                // 发生 IO 错误时主动释放资源，避免半开状态
                disconnect()
            }
        }

        ioManager = SerialInputOutputManager(port, listener).also {
            it.readBufferSize = 4096
            it.start()
        }
    }

    fun write(data: ByteArray) {
        try {
            val port = serialPort
            if (port == null) {
                val message = "TX failed, port not connected: ${data.toHexPreview()}"
                Log.w(TAG, message)
                DiagnosticLog.add(TAG, message)
                return
            }
            val message = "TX ${data.size} bytes: ${data.toHexPreview()}"
            Log.d(TAG, message)
            DiagnosticLog.add(TAG, message)
            port.write(data, WRITE_TIMEOUT_MS)
        } catch (e: IOException) {
            Log.e(TAG, "写入数据失败", e)
            DiagnosticLog.add(TAG, "write failed: ${e.message}")
            _connectionState.value = ConnectionState.Error("发送数据失败: ${e.message}")
        }
    }

    private fun ByteArray.toHexPreview(): String {
        val count = minOf(size, HEX_PREVIEW_BYTES)
        val hex = asSequence()
            .take(count)
            .joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        return if (size > count) "$hex ... +${size - count} bytes" else hex
    }

    fun disconnect() {
        ioManager?.listener = null
        ioManager?.stop()
        ioManager = null

        try {
            serialPort?.close()
        } catch (e: IOException) {
            Log.w(TAG, "关闭串口异常", e)
        }
        serialPort = null

        connection?.close()
        connection = null

        _connectionState.value = ConnectionState.Disconnected
        Log.i(TAG, "串口已断开")
        DiagnosticLog.add(TAG, "disconnected")
    }

    val isConnected: Boolean
        get() = _connectionState.value is ConnectionState.Connected
}
