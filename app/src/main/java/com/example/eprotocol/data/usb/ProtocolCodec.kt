package com.example.eprotocol.data.usb

import android.util.Log
import com.example.eprotocol.data.model.CalibrationData
import com.example.eprotocol.data.model.DataPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 协议编解码器
 *
 * 根据通讯协议 V1.00 负责:
 * 1. 构建各种 Hex 指令 (OCP, i-t曲线, 量程切换, 查找仪器, 开始/停止采样等)
 * 2. 接收流式字节数据，通过内部缓冲区拼接出完整数据包
 * 3. 将完整数据包解析为 DataPoint (电压/电流对)
 *
 * 缓冲区使用 ByteArray + writePos 实现，避免 MutableList 的 O(n) 头删性能问题。
 * 在找不到包头时保留尾部 HEADER_SIZE-1 字节，防止跨分包的包头前缀被误删。
 */
class ProtocolCodec {

    companion object {
        private const val TAG = "ProtocolCodec"

        private val HEADER = byteArrayOf(0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00)
        private const val HEADER_SIZE = 6
        private const val FIND_DEVICE_RESPONSE_SIZE = 91
        private const val GROUP_SIZE = 4
        private val TRAILER = byteArrayOf(0x0D, 0x0A)

        private const val ADC_FULL_SCALE = 10.0
        private const val ADC_RESOLUTION = 32768.0
        private const val NANOAMP_TO_AMP = 1e-9

        // 缓冲区最大容量 (足够容纳多个最大数据包)
        private const val BUFFER_CAPACITY = 8192

        const val OCP_GROUPS_PER_PACKET = 20
        const val IT_GROUPS_PER_PACKET = 20

        private val VALID_GROUP_COUNTS = intArrayOf(20, 5, 1)

        /**
         * PC/Win 软件中的 sampleResistance[range] 等效表。
         * 电流换算必须先把 ADC 原始值转换为 10 V 满量程下的电压量，
         * 再除以当前量程对应的采样电阻/比例系数。
         */
        private fun sampleResistanceForRange(rangeCode: Int): Double {
            return when (rangeCode) {
                0x02 -> 100.0
                0x03 -> 1_000.0
                0x04 -> 10_000.0
                0x05 -> 100_000.0
                0x06 -> 1_000_000.0
                0x07 -> 10_000_000.0
                0x08 -> 100_000_000.0
                else -> 1_000_000.0
            }
        }
    }

    var expectedGroupsPerPacket: Int = IT_GROUPS_PER_PACKET

    private val _parsedDataFlow = MutableSharedFlow<List<DataPoint>>(extraBufferCapacity = 128)
    val parsedDataFlow: SharedFlow<List<DataPoint>> = _parsedDataFlow.asSharedFlow()

    private val _responseFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)
    val responseFlow: SharedFlow<ByteArray> = _responseFlow.asSharedFlow()

    // 高性能 ByteArray 缓冲区
    private var buffer = ByteArray(BUFFER_CAPACITY)
    private var bufferLen = 0
    private val bufferLock = Object()

    var calibration: CalibrationData = CalibrationData()
        private set

    /**
     * 设备是否已返回校准数据。
     * TestOrchestrator 在启动测试前检查此标志，为 false 时拒绝开始。
     */
    var isCalibrated: Boolean = false
        private set

    var currentRangeIndex: Int = 5
        private set

    var currentRangeCode: Int = 0x07
        private set

    // =========================================================================
    //  指令构建
    // =========================================================================

    fun buildFindDeviceCmd(): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x01, 0x01, 0x00, 0x00, 0x00,
            0x0D, 0x0A
        )
    }

    fun buildSetCurrentRangeCmd(rangeCode: Int): ByteArray {
        currentRangeCode = rangeCode
        currentRangeIndex = rangeCode - 2
        DiagnosticLog.add(
            TAG,
            "set current range code=0x${"%02X".format(rangeCode)} sampleResistance=${sampleResistanceForRange(rangeCode)}"
        )
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x02, 0x02, 0x00, rangeCode.toByte(),
            0x0D, 0x0A
        )
    }

    fun buildOcpCmd(durationSec: Float, intervalSec: Float): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x0A, 0x03, 0x00
        ) + floatToBytes(durationSec) + floatToBytes(intervalSec) + TRAILER
    }

    fun buildItCurveCmd(
        voltageV: Float,
        runtimeSec: Float,
        intervalSec: Float,
        quietTimeSec: Float = 0f
    ): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x13, 0x03, 0x04
        ) + floatToBytes(voltageV) +
                floatToBytes(runtimeSec) +
                floatToBytes(intervalSec) +
                floatToBytes(quietTimeSec) +
                TRAILER
    }

    fun buildStartSamplingCmd(): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x03, 0x08, 0x01, 0x00,
            0x0D, 0x0A
        )
    }

    fun buildStopSamplingCmd(): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x03, 0x08, 0x02, 0x00,
            0x0D, 0x0A
        )
    }

    fun buildReadOcpCmd(): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x03, 0x08, 0x04, 0x00,
            0x0D, 0x0A
        )
    }

    fun buildCloseCmd(): ByteArray {
        return byteArrayOf(
            0x55, 0xAA.toByte(), 0x00, 0x00, 0x00, 0x00,
            0x00, 0x03, 0x08, 0x05, 0x00,
            0x0D, 0x0A
        )
    }

    // =========================================================================
    //  数据接收与解析
    // =========================================================================

    fun feedData(chunk: ByteArray) {
        DiagnosticLog.add(TAG, "feed ${chunk.size} bytes")
        synchronized(bufferLock) {
            ensureCapacity(chunk.size)
            System.arraycopy(chunk, 0, buffer, bufferLen, chunk.size)
            bufferLen += chunk.size
            processBuffer()
        }
    }

    fun clearBuffer() {
        synchronized(bufferLock) {
            bufferLen = 0
        }
    }

    /**
     * 确保缓冲区有足够空间容纳新数据。
     * 空间不足时进行扩容。
     */
    private fun ensureCapacity(additionalSize: Int) {
        val required = bufferLen + additionalSize
        if (required > buffer.size) {
            val newSize = maxOf(buffer.size * 2, required)
            val newBuffer = ByteArray(newSize)
            System.arraycopy(buffer, 0, newBuffer, 0, bufferLen)
            buffer = newBuffer
        }
    }

    /**
     * 从缓冲区中消费已处理的字节。
     * 将 [start, bufferLen) 区间的数据移到缓冲区开头。
     */
    private fun consumeBytes(count: Int) {
        if (count >= bufferLen) {
            bufferLen = 0
        } else {
            System.arraycopy(buffer, count, buffer, 0, bufferLen - count)
            bufferLen -= count
        }
    }

    private fun processBuffer() {
        while (true) {
            val headerPos = findHeader()
            if (headerPos < 0) {
                // 保留尾部可能是不完整包头的字节 (最多 HEADER_SIZE-1 = 5 字节)
                val keep = minOf(bufferLen, HEADER_SIZE - 1)
                if (keep > 0 && keep < bufferLen) {
                    System.arraycopy(buffer, bufferLen - keep, buffer, 0, keep)
                }
                bufferLen = keep
                return
            }

            // 丢弃包头之前的无效数据
            if (headerPos > 0) {
                consumeBytes(headerPos)
            }

            // 判断是否为设备响应 (查找仪器响应: 55 AA 00 00 00 00 01 01 01 ...)
            if (bufferLen >= 9 &&
                buffer[6] == 0x01.toByte() &&
                buffer[7] == 0x01.toByte() &&
                buffer[8] == 0x01.toByte()
            ) {
                if (!handleDeviceResponse()) return
                continue
            }

            if (isControlResponse()) {
                if (!handleControlResponse()) return
                continue
            }

            // 采样数据包没有 0D 0A 结尾，实际设备可能按 1/5/20 组返回。
            // 优先用下一个包头判断当前包边界；若还没等到下一个包头，则按当前实验期望长度兜底。
            val packetSize = findDelimitedPacketSize() ?: findBufferedDataPacketSize() ?: return

            // 提取完整数据包
            val packet = ByteArray(packetSize)
            System.arraycopy(buffer, 0, packet, 0, packetSize)
            consumeBytes(packetSize)

            val dataPoints = parseDataPacket(packet)
            if (dataPoints.isNotEmpty()) {
                _parsedDataFlow.tryEmit(dataPoints)
            }
        }
    }

    /**
     * 处理设备响应包，返回 true 表示成功消费了包，false 表示数据不足需等待。
     */
    private fun handleDeviceResponse(): Boolean {
        // 查找仪器响应的协议长度固定为 91 字节。部分设备末尾不是文档中的 0D 0A，
        // 而是 00 00，因此不能只依赖尾部标记判断完整包。
        if (bufferLen >= FIND_DEVICE_RESPONSE_SIZE) {
            val packet = ByteArray(FIND_DEVICE_RESPONSE_SIZE)
            System.arraycopy(buffer, 0, packet, 0, FIND_DEVICE_RESPONSE_SIZE)
            consumeBytes(FIND_DEVICE_RESPONSE_SIZE)
            parseDeviceResponse(packet)
            return true
        }

        // 兼容实际带 0D 0A 结尾但被分片传输的响应。
        val trailerPos = findTrailerFrom(9)
        if (trailerPos < 0) {
            return false
        }

        val packetLen = trailerPos + 2
        val packet = ByteArray(packetLen)
        System.arraycopy(buffer, 0, packet, 0, packetLen)
        consumeBytes(packetLen)
        parseDeviceResponse(packet)
        return true
    }

    private fun parseDeviceResponse(packet: ByteArray) {
        try {
            calibration = parseFindDeviceResponse(packet)
            isCalibrated = true
            val tail = packet.takeLast(2).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            Log.i(TAG, "设备校准数据已解析: voltCoeff=${calibration.voltageCoeff}")
            DiagnosticLog.add(
                TAG,
                "calibration parsed size=${packet.size} tail=$tail " +
                        "voltCoeff=${calibration.voltageCoeff} voltZeroMv=${calibration.voltageZero}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "解析设备响应失败", e)
            DiagnosticLog.add(TAG, "calibration parse failed: ${e.message}")
        }
        _responseFlow.tryEmit(packet)
    }

    private fun isControlResponse(): Boolean {
        if (bufferLen < 11) return false

        val command = buffer[9].toInt() and 0xFF
        val isKnownCommand = command == 0x01 ||
                command == 0x02 ||
                command == 0x04 ||
                command == 0x05

        return isKnownCommand &&
                buffer[6] == 0x00.toByte() &&
                buffer[7] == 0x03.toByte() &&
                buffer[8] == 0x08.toByte() &&
                buffer[10] == 0x00.toByte()
    }

    private fun handleControlResponse(): Boolean {
        if (bufferLen < 11) return false

        val packetLen = if (
            bufferLen >= 13 &&
            buffer[11] == 0x0D.toByte() &&
            buffer[12] == 0x0A.toByte()
        ) {
            13
        } else {
            11
        }

        val command = buffer[9].toInt() and 0xFF
        val packet = ByteArray(packetLen)
        System.arraycopy(buffer, 0, packet, 0, packetLen)
        consumeBytes(packetLen)

        DiagnosticLog.add(TAG, "control response cmd=0x${"%02X".format(command)} size=$packetLen")
        _responseFlow.tryEmit(packet)
        return true
    }

    private fun findTrailerFrom(startPos: Int): Int {
        for (i in startPos until bufferLen - 1) {
            if (buffer[i] == 0x0D.toByte() && buffer[i + 1] == 0x0A.toByte()) {
                return i
            }
        }
        return -1
    }

    private fun findDelimitedPacketSize(): Int? {
        val nextHeaderPos = findHeader(HEADER_SIZE)
        if (nextHeaderPos < 0) return null

        val payloadSize = nextHeaderPos - HEADER_SIZE
        if (payloadSize <= 0 || payloadSize % GROUP_SIZE != 0) {
            return null
        }

        val groupCount = payloadSize / GROUP_SIZE
        if (groupCount != 1 && groupCount != 5 && groupCount != 20) {
            return null
        }

        return nextHeaderPos
    }

    private fun findBufferedDataPacketSize(): Int? {
        val expectedGroups = expectedGroupsPerPacket.coerceAtLeast(1)
        val preferredGroups = VALID_GROUP_COUNTS.firstOrNull { groupCount ->
            groupCount >= expectedGroups && bufferLen >= HEADER_SIZE + groupCount * GROUP_SIZE
        }

        if (preferredGroups != null) {
            return HEADER_SIZE + preferredGroups * GROUP_SIZE
        }

        val expectedSize = HEADER_SIZE + expectedGroups * GROUP_SIZE
        return if (bufferLen >= expectedSize) expectedSize else null
    }

    private fun findHeader(startIndex: Int = 0): Int {
        if (bufferLen < HEADER_SIZE || startIndex > bufferLen - HEADER_SIZE) return -1
        for (i in startIndex..bufferLen - HEADER_SIZE) {
            if (buffer[i] == HEADER[0] && buffer[i + 1] == HEADER[1] &&
                buffer[i + 2] == HEADER[2] && buffer[i + 3] == HEADER[3] &&
                buffer[i + 4] == HEADER[4] && buffer[i + 5] == HEADER[5]
            ) {
                return i
            }
        }
        return -1
    }

    // =========================================================================
    //  数据包解析
    // =========================================================================

    fun parseDataPacket(raw: ByteArray): List<DataPoint> {
        if (raw.size < HEADER_SIZE + GROUP_SIZE) return emptyList()

        val dataLen = raw.size - HEADER_SIZE
        val groupCount = dataLen / GROUP_SIZE
        val points = mutableListOf<DataPoint>()

        for (i in 0 until groupCount) {
            val offset = HEADER_SIZE + i * GROUP_SIZE
            // Win 软件 i-t 数据通道顺序：byte[6-7] = currentRaw，byte[8-9] = voltageRaw。
            // 这里统一主解析字段，避免 App 拟合/显示与 Win 软件通道相反。
            val currentRaw = parseRawValue(raw[offset], raw[offset + 1])
            val voltageRaw = parseRawValue(raw[offset + 2], raw[offset + 3])
            points.add(
                DataPoint(
                    voltage = rawToVoltage(voltageRaw),
                    current = rawToCurrent(currentRaw),
                    firstRaw = currentRaw,
                    secondRaw = voltageRaw,
                    secondAsVoltage = rawToVoltage(voltageRaw)
                )
            )
        }

        DiagnosticLog.add(TAG, "parsed data packet raw=${raw.size} bytes groups=$groupCount points=${points.size}")
        return points
    }

    fun parseRawValue(highByte: Byte, lowByte: Byte): Int {
        val value = (highByte.toInt() shl 8) or (lowByte.toInt() and 0xFF)
        return if (value > 32767) value - 65536 else value
    }

    fun rawToVoltage(rawInt: Int): Double {
        val base = ADC_FULL_SCALE * rawInt / ADC_RESOLUTION
        // Win 软件公式：E = -raw * 10 / 32768 * voltageCoeff + voltageZero。
        // 协议中的 voltageZero 单位为 mV，因此 App 内部换算为 V。
        return -base * calibration.voltageCoeff + calibration.voltageZero / 1000.0
    }

    fun rawToCurrent(rawInt: Int): Double {
        val rangeIdx = currentRangeIndex.coerceIn(0, calibration.currentCoeffs.size - 1)
        val coeff = calibration.currentCoeffs[rangeIdx]
        val zero = calibration.currentZeros[rangeIdx]
        // Win 软件公式：I(A) = raw * 10 / 32768 / sampleResistance[range]
        //                I(A) = I(A) * currentCoeff[range] + currentZero[range] * 1e-9
        val baseCurrentAmp = ADC_FULL_SCALE * rawInt / ADC_RESOLUTION / sampleResistanceForRange(currentRangeCode)
        return baseCurrentAmp * coeff + zero * NANOAMP_TO_AMP
    }

    fun parseFindDeviceResponse(raw: ByteArray): CalibrationData {
        val dataStart = 9
        val bb = ByteBuffer.wrap(raw, dataStart, raw.size - dataStart - 2)
        bb.order(ByteOrder.BIG_ENDIAN)

        val voltCoeff = bb.float
        val curCoeffs = FloatArray(9) { bb.float }
        val voltZero = bb.float
        val curZeros = FloatArray(9) { bb.float }

        return CalibrationData(voltCoeff, curCoeffs, voltZero, curZeros)
    }

    fun floatToBytes(value: Float): ByteArray {
        val bb = ByteBuffer.allocate(4)
        bb.order(ByteOrder.BIG_ENDIAN)
        bb.putFloat(value)
        return bb.array()
    }
}
