package com.example.eprotocol.domain

import android.util.Log
import com.example.eprotocol.BuildConfig
import com.example.eprotocol.data.model.DataPoint
import com.example.eprotocol.data.model.OrchestratorState
import com.example.eprotocol.data.model.TestStep
import com.example.eprotocol.data.usb.DiagnosticLog
import com.example.eprotocol.data.usb.ProtocolCodec
import com.example.eprotocol.data.usb.UsbSerialManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.abs

/**
 * 测试流程编排器
 *
 * 使用协程按顺序执行 6 个步骤的电化学测试流程。
 * 全过程可随时通过协程取消来中止，取消时在 NonCancellable 上下文中
 * 发送停止采样命令，确保设备不会处于异常运行状态。
 */
class TestOrchestrator(
    private val usbManager: UsbSerialManager,
    private val codec: ProtocolCodec
) {

    companion object {
        private const val TAG = "TestOrchestrator"

        private const val OCP_DURATION_SEC = 40f
        private const val OCP_INTERVAL_SEC = 0.1f
        private const val OCP_INTERVAL_MS = 100L
        private const val OCP_AVERAGE_WINDOW_MS = 3000L
        private const val OCP_READ_PROBE_MS = 1500L
        private const val OCP_SECOND_CHANNEL_POLARITY = -1.0

        private const val CV_DURATION_SEC = 8f
        private const val CV_INTERVAL_SEC = 0.1f
        private const val CV_AVERAGE_WINDOW_MS = 1000L

        private val COARSE_OFFSETS_MV = listOf(-100.0, 0.0, 100.0)

        private const val FINE_RANGE_MV = 10.0
        private const val FINE_STEP_MV = 5.0

        // 与本次小电流 i-t 测量对齐：固定使用 0x06（1 uA 档）。
        private const val DEFAULT_CURRENT_RANGE = 0x06

        private val IT_INTERVAL_BETWEEN_MEASUREMENTS_MS = BuildConfig.IT_INTERVAL_BETWEEN_MEASUREMENTS_MS
        private const val POST_EXPERIMENT_DELAY_MS = 1500L
        private const val CALIBRATION_TIMEOUT_MS = 5000L
        private const val CALIBRATION_POLL_MS = 200L
        private const val CALIBRATION_RETRY_MS = 1000L

        // 进度权重
        private const val OCP_WEIGHT = 22         // 40s -> 0-22%
        private const val COARSE_WEIGHT = 15
        private const val FIT_E0_WEIGHT = 1       // 瞬时 -> 37-38%
        private const val FINE_WEIGHT = 61
        private const val FIT_EE_WEIGHT = 1        // 瞬时 -> 99-100%
    }

    private val _state = MutableStateFlow<OrchestratorState>(OrchestratorState.Idle)
    val state: StateFlow<OrchestratorState> = _state.asStateFlow()

    suspend fun runTest() {
        try {
            // === 校准检查 ===
            if (!codec.isCalibrated) {
                _state.value = OrchestratorState.Running(TestStep.MEASURE_EI, "正在查找仪器...")
                codec.clearBuffer()
                val calibrated = waitForCalibration()
                if (!calibrated) {
                    _state.value = OrchestratorState.Error("未收到仪器校准数据，请检查设备连接")
                    return
                }
            }

            // === 步骤 1: 测定开路电位 (OCP) ===
            _state.value = OrchestratorState.Running(TestStep.MEASURE_EI, "正在测定开路电位 (OCP)...")
            val ei = measureOcp()
            Log.i(TAG, "E_i = $ei mV")
            DiagnosticLog.add(TAG, "E_i=${fmt(ei)} mV")
            _state.value = OrchestratorState.StepResult(TestStep.MEASURE_EI, ei = ei)
            delay(300)

            // === 步骤 2: 粗调恒电位扫描 ===
            _state.value = OrchestratorState.Running(TestStep.COARSE_SCAN, "正在进行粗调恒电位扫描...")
            val coarseData = coarseScan(ei)
            Log.i(TAG, "粗调数据: ${coarseData.size} 组")

            // === 步骤 3: 第一次线性拟合 -> E_0 ===
            emitProgress(TestStep.FIT_E0, "正在拟合计算 E_0...", OCP_WEIGHT + COARSE_WEIGHT)
            val firstFit = MathUtils.linearRegression(coarseData)
            val e0 = firstFit.intercept * 1000.0
            Log.i(TAG, "E_0 = $e0 mV (R^2 = ${firstFit.rSquared})")
            DiagnosticLog.add(TAG, "E_0=${fmt(e0)} mV r2=${fmt(firstFit.rSquared)}")
            _state.value = OrchestratorState.StepResult(TestStep.FIT_E0, ei = ei, e0 = e0)
            delayBeforeNextItMeasurement(
                TestStep.FINE_SCAN,
                OCP_WEIGHT + COARSE_WEIGHT + FIT_E0_WEIGHT,
                "等待细调 i-t"
            )

            // === 步骤 4: 细调恒电位扫描 ===
            _state.value = OrchestratorState.Running(TestStep.FINE_SCAN, "正在进行细调恒电位扫描...")
            val fineData = fineScan(e0)
            Log.i(TAG, "细调数据: ${fineData.size} 组")

            // === 步骤 5: 第二次线性拟合 + 异常点剔除 -> Eh ===
            emitProgress(TestStep.FIT_EE, "正在拟合计算 Eh...", OCP_WEIGHT + COARSE_WEIGHT + FIT_E0_WEIGHT + FINE_WEIGHT)
            val secondFit = MathUtils.fitWithOutlierRemoval(fineData)
            val ee = secondFit.intercept * 1000.0
            Log.i(TAG, "Eh = $ee mV (R^2 = ${secondFit.rSquared})")
            DiagnosticLog.add(TAG, "Eh=${fmt(ee)} mV r2=${fmt(secondFit.rSquared)}")

            // === 步骤 6: 完成 ===
            _state.value = OrchestratorState.Completed(
                ee = ee,
                rSquared = secondFit.rSquared,
                fittingPoints = fineData,
                fittingResult = secondFit
            )
            Log.i(TAG, "测试流程完成")
            DiagnosticLog.add(TAG, "test completed")

        } catch (e: CancellationException) {
            Log.i(TAG, "测试流程被用户取消")
            // 在不可取消上下文中发送停止命令，确保设备停止
            withContext(NonCancellable) {
                sendStopSampling()
            }
            _state.value = OrchestratorState.Idle
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "测试流程异常", e)
            DiagnosticLog.add(TAG, "test error: ${e.message}")
            withContext(NonCancellable) {
                sendStopSampling()
            }
            _state.value = OrchestratorState.Error("测试出错: ${e.message}")
        }
    }

    fun reset() {
        _state.value = OrchestratorState.Idle
    }

    // =========================================================================
    //  校准等待
    // =========================================================================

    private suspend fun waitForCalibration(): Boolean {
        val deadline = System.currentTimeMillis() + CALIBRATION_TIMEOUT_MS
        var nextProbeAt = 0L
        while (System.currentTimeMillis() < deadline) {
            if (codec.isCalibrated) return true
            val now = System.currentTimeMillis()
            if (now >= nextProbeAt) {
                usbManager.write(codec.buildFindDeviceCmd())
                nextProbeAt = now + CALIBRATION_RETRY_MS
            }
            delay(CALIBRATION_POLL_MS)
        }
        return codec.isCalibrated
    }

    // =========================================================================
    //  进度计算与发射
    // =========================================================================

    private fun emitProgress(step: TestStep, message: String, percent: Int, chartPoints: List<Pair<Float, Float>> = emptyList()) {
        _state.value = OrchestratorState.LiveDataUpdate(
            step = step,
            message = message,
            progressPercent = percent.coerceIn(0, 100),
            chartPoints = chartPoints
        )
    }

    // =========================================================================
    //  步骤 1: OCP
    // =========================================================================

    private enum class OcpChannel(val label: String) {
        FIRST("first"),
        SECOND("second")
    }

    private data class OcpSample(
        val timeMs: Long,
        val firstVoltage: Double,
        val secondVoltage: Double,
        val firstRaw: Int,
        val secondRaw: Int
    ) {
        fun voltage(channel: OcpChannel): Double {
            return if (channel == OcpChannel.SECOND) secondVoltage else firstVoltage
        }

        fun raw(channel: OcpChannel): Int {
            return if (channel == OcpChannel.SECOND) secondRaw else firstRaw
        }
    }

    private data class OcpResult(
        val eiMv: Double,
        val source: String,
        val channel: OcpChannel
    )

    private suspend fun measureOcp(): Double {
        val result = measureOcpByReadCommand()
            ?: run {
                DiagnosticLog.add(TAG, "OCP read-command produced no data; fallback to OCP experiment")
                measureOcpByExperiment()
            }
            ?: throw IllegalStateException("OCP 未收集到有效数据")

        DiagnosticLog.add(TAG, "OCP selected source=${result.source} channel=${result.channel.label}")
        return result.eiMv
    }

    private suspend fun measureOcpByReadCommand(): OcpResult? {
        codec.expectedGroupsPerPacket = 1
        codec.clearBuffer()

        val samples = mutableListOf<OcpSample>()
        val totalDurationMs = (OCP_DURATION_SEC * 1000).toLong()
        val startTime = System.currentTimeMillis()
        val deadline = startTime + totalDurationMs

        DiagnosticLog.add(TAG, "OCP read-command start duration=${OCP_DURATION_SEC}s interval=${OCP_INTERVAL_SEC}s")

        coroutineScope {
            val collector = launch {
                codec.parsedDataFlow.collect { points ->
                    val now = System.currentTimeMillis()
                    addOcpSamples(points, now, samples)
                    emitOcpProgress(samples, startTime, now)
                }
            }

            try {
                val probeDeadline = startTime + OCP_READ_PROBE_MS
                while (System.currentTimeMillis() < deadline) {
                    usbManager.write(codec.buildReadOcpCmd())
                    delay(OCP_INTERVAL_MS)
                    if (samples.isEmpty() && System.currentTimeMillis() >= probeDeadline) {
                        DiagnosticLog.add(TAG, "OCP read-command no data within ${OCP_READ_PROBE_MS}ms")
                        break
                    }
                }
                if (samples.isNotEmpty()) {
                    delay(POST_EXPERIMENT_DELAY_MS)
                }
            } finally {
                collector.cancelAndJoin()
            }
        }

        return buildOcpResult("read-command", samples, deadline)
    }

    private suspend fun measureOcpByExperiment(): OcpResult? {
        codec.expectedGroupsPerPacket = ProtocolCodec.OCP_GROUPS_PER_PACKET
        codec.clearBuffer()

        DiagnosticLog.add(TAG, "OCP experiment start duration=${OCP_DURATION_SEC}s interval=${OCP_INTERVAL_SEC}s")
        usbManager.write(codec.buildOcpCmd(OCP_DURATION_SEC, OCP_INTERVAL_SEC))
        delay(200)
        usbManager.write(codec.buildStartSamplingCmd())

        val samples = mutableListOf<OcpSample>()
        val totalDurationMs = (OCP_DURATION_SEC * 1000).toLong()
        val startTime = System.currentTimeMillis()
        val deadline = startTime + totalDurationMs

        collectDataForDuration(totalDurationMs) { points ->
            val now = System.currentTimeMillis()
            addOcpSamples(points, now, samples)
            emitOcpProgress(samples, startTime, now)
        }

        sendStopSampling()

        return buildOcpResult("experiment", samples, deadline)
    }

    private fun addOcpSamples(
        points: List<DataPoint>,
        timeMs: Long,
        samples: MutableList<OcpSample>
    ) {
        for (p in points) {
            samples.add(
                OcpSample(
                    timeMs = timeMs,
                    firstVoltage = p.voltage,
                    // PC 软件把 08 04 返回的第二字段按反向极性显示为 OCP。
                    secondVoltage = p.secondAsVoltage * OCP_SECOND_CHANNEL_POLARITY,
                    firstRaw = p.firstRaw,
                    secondRaw = p.secondRaw
                )
            )
        }
    }

    private fun emitOcpProgress(
        samples: List<OcpSample>,
        startTime: Long,
        now: Long
    ) {
        if (samples.isEmpty()) return

        val channel = selectOcpChannel(samples, now)
        val chartPoints = samples.map { sample ->
            Pair(
                ((sample.timeMs - startTime) / 1000f),
                (sample.voltage(channel) * 1000).toFloat()
            )
        }
        val elapsed = ((now - startTime) / 1000).coerceAtMost(OCP_DURATION_SEC.toLong())
        val stepProgress = (elapsed.toFloat() / OCP_DURATION_SEC * OCP_WEIGHT).toInt()
        emitProgress(
            TestStep.MEASURE_EI,
            "OCP 采集中 (${elapsed}/${OCP_DURATION_SEC.toInt()}s)...",
            stepProgress,
            chartPoints
        )
    }

    private fun buildOcpResult(
        source: String,
        samples: List<OcpSample>,
        deadline: Long
    ): OcpResult? {
        if (samples.isEmpty()) return null

        val channel = selectOcpChannel(samples, deadline)
        val lastSamples = samples
            .filter { it.timeMs >= deadline - OCP_AVERAGE_WINDOW_MS }
            .ifEmpty { samples }
        val lastVoltages = lastSamples.map { it.voltage(channel) }
        val allVoltages = samples.map { it.voltage(channel) }
        val lastFirstVoltages = lastSamples.map { it.firstVoltage }
        val lastSecondVoltages = lastSamples.map { it.secondVoltage }
        val lastFirstRaw = lastSamples.map { it.firstRaw.toDouble() }
        val lastSecondRaw = lastSamples.map { it.secondRaw.toDouble() }

        val eiMv = MathUtils.average(lastVoltages) * 1000.0
        DiagnosticLog.add(
            TAG,
            "OCP summary source=$source totalPoints=${samples.size} averageWindowMs=$OCP_AVERAGE_WINDOW_MS " +
                    "channel=${channel.label} avgMv=${fmt(eiMv)} " +
                    "lastMinMv=${fmt(lastVoltages.minOrNull()!! * 1000.0)} " +
                    "lastMaxMv=${fmt(lastVoltages.maxOrNull()!! * 1000.0)} " +
                    "allMinMv=${fmt(allVoltages.minOrNull()!! * 1000.0)} " +
                    "allMaxMv=${fmt(allVoltages.maxOrNull()!! * 1000.0)} " +
                    "firstAvgMv=${fmt(MathUtils.average(lastFirstVoltages) * 1000.0)} " +
                    "secondAvgMv=${fmt(MathUtils.average(lastSecondVoltages) * 1000.0)} " +
                    "firstRawAvg=${fmt(MathUtils.average(lastFirstRaw))} " +
                    "secondRawAvg=${fmt(MathUtils.average(lastSecondRaw))}"
        )

        return OcpResult(eiMv = eiMv, source = source, channel = channel)
    }

    private fun selectOcpChannel(samples: List<OcpSample>, referenceTimeMs: Long): OcpChannel {
        if (samples.size < 3) return OcpChannel.FIRST

        val window = samples
            .filter { it.timeMs >= referenceTimeMs - OCP_AVERAGE_WINDOW_MS }
            .ifEmpty {
                samples
            }

        val firstMv = window.map { it.firstVoltage * 1000.0 }
        val secondMv = window.map { it.secondVoltage * 1000.0 }
        val firstAvg = MathUtils.average(firstMv)
        val secondAvg = MathUtils.average(secondMv)
        val firstSpan = firstMv.maxOrNull()!! - firstMv.minOrNull()!!
        val secondSpan = secondMv.maxOrNull()!! - secondMv.minOrNull()!!

        val firstLooksPinnedNearZero = abs(firstAvg) < 20.0 && firstSpan < 10.0
        val secondLooksLikePotential = abs(secondAvg) > 10.0 || secondSpan > firstSpan * 3.0
        return if (firstLooksPinnedNearZero && secondLooksLikePotential) {
            OcpChannel.SECOND
        } else {
            OcpChannel.FIRST
        }
    }

    // =========================================================================
    //  步骤 2: 粗调
    // =========================================================================

    private suspend fun delayBeforeNextItMeasurement(
        step: TestStep,
        progress: Int,
        label: String
    ) {
        val seconds = IT_INTERVAL_BETWEEN_MEASUREMENTS_MS / 1000
        DiagnosticLog.add(TAG, "IT interval before next measurement seconds=$seconds")
        emitProgress(step, "$label，${seconds}s 后继续...", progress)
        delay(IT_INTERVAL_BETWEEN_MEASUREMENTS_MS)
    }

    private suspend fun coarseScan(eiMv: Double): List<Pair<Double, Double>> {
        codec.expectedGroupsPerPacket = ProtocolCodec.IT_GROUPS_PER_PACKET
        val result = mutableListOf<Pair<Double, Double>>()

        for ((idx, offsetMv) in COARSE_OFFSETS_MV.withIndex()) {
            val voltageMv = eiMv + offsetMv
            val voltageV = voltageMv / 1000.0

            val stepBase = OCP_WEIGHT + (idx.toFloat() / COARSE_OFFSETS_MV.size * COARSE_WEIGHT).toInt()
            emitProgress(
                TestStep.COARSE_SCAN,
                "粗调 ${idx + 1}/${COARSE_OFFSETS_MV.size}: ${String.format("%.1f", voltageMv)} mV",
                stepBase
            )

            val avgCurrent = runConstantVoltage(voltageV.toFloat(), TestStep.COARSE_SCAN, stepBase)
            result.add(Pair(avgCurrent, voltageV))

            if (idx < COARSE_OFFSETS_MV.size - 1) {
                delayBeforeNextItMeasurement(
                    TestStep.COARSE_SCAN,
                    stepBase,
                    "等待下一次粗调 i-t"
                )
            }
        }

        return result
    }

    // =========================================================================
    //  步骤 4: 细调
    // =========================================================================

    private suspend fun fineScan(e0Mv: Double): List<Pair<Double, Double>> {
        codec.expectedGroupsPerPacket = ProtocolCodec.IT_GROUPS_PER_PACKET
        val result = mutableListOf<Pair<Double, Double>>()

        val voltagePoints = mutableListOf<Double>()
        var v = e0Mv - FINE_RANGE_MV
        while (v <= e0Mv + FINE_RANGE_MV + 0.001) {
            voltagePoints.add(v)
            v += FINE_STEP_MV
        }

        val fineBase = OCP_WEIGHT + COARSE_WEIGHT + FIT_E0_WEIGHT

        for ((idx, voltageMv) in voltagePoints.withIndex()) {
            val voltageV = voltageMv / 1000.0

            val stepBase = fineBase + (idx.toFloat() / voltagePoints.size * FINE_WEIGHT).toInt()
            emitProgress(
                TestStep.FINE_SCAN,
                "细调 ${idx + 1}/${voltagePoints.size}: ${String.format("%.1f", voltageMv)} mV",
                stepBase
            )

            val avgCurrent = runConstantVoltage(voltageV.toFloat(), TestStep.FINE_SCAN, stepBase)
            result.add(Pair(avgCurrent, voltageV))

            if (idx < voltagePoints.size - 1) {
                delayBeforeNextItMeasurement(
                    TestStep.FINE_SCAN,
                    stepBase,
                    "等待下一次细调 i-t"
                )
            }
        }

        return result
    }

    // =========================================================================
    //  恒电位实验
    // =========================================================================

    private suspend fun runConstantVoltage(voltageV: Float, step: TestStep, baseProgress: Int): Double {
        codec.clearBuffer()
        usbManager.write(codec.buildSetCurrentRangeCmd(DEFAULT_CURRENT_RANGE))
        delay(200)
        codec.clearBuffer()

        val voltageMv = voltageV.toDouble() * 1000.0
        DiagnosticLog.add(
            TAG,
            "IT start step=${step.name} voltageMv=${fmt(voltageMv)} currentRange=0x${"%02X".format(DEFAULT_CURRENT_RANGE)} " +
                    "commandVoltageV=${fmt(voltageV.toDouble())} note=Win-aligned-no-inversion"
        )
        // Win 软件直接发送界面目标电位；这里不再对 i-t 施加电位自动取负号。
        usbManager.write(codec.buildItCurveCmd(voltageV, CV_DURATION_SEC, CV_INTERVAL_SEC))
        delay(200)
        usbManager.write(codec.buildStartSamplingCmd())

        val allCurrents = mutableListOf<Pair<Long, Double>>()
        val chartPoints = mutableListOf<Pair<Float, Float>>()
        val totalDurationMs = (CV_DURATION_SEC * 1000).toLong()
        val startTime = System.currentTimeMillis()
        val deadline = startTime + totalDurationMs

        collectDataForDuration(totalDurationMs) { points ->
            val now = System.currentTimeMillis()
            for (p in points) {
                allCurrents.add(now to p.current)
                chartPoints.add(Pair(
                    ((now - startTime) / 1000f),
                    p.current.toFloat()
                ))
            }
        }

        sendStopSampling()

        val lastCurrents = allCurrents
            .filter { it.first >= deadline - CV_AVERAGE_WINDOW_MS }
            .map { it.second }
            .ifEmpty {
                allCurrents.map { it.second }
            }

        if (lastCurrents.isEmpty()) {
            throw IllegalStateException("恒电位实验未收集到有效电流数据 (V=${voltageV})")
        }

        val avgCurrent = MathUtils.average(lastCurrents)
        DiagnosticLog.add(
            TAG,
            "IT summary step=${step.name} voltageMv=${fmt(voltageMv)} " +
                    "totalPoints=${allCurrents.size} averageWindowMs=$CV_AVERAGE_WINDOW_MS " +
                    "avgCurrent=${fmtScientific(avgCurrent)}"
        )
        return avgCurrent
    }

    // =========================================================================
    //  数据收集
    // =========================================================================

    private suspend fun collectDataForDuration(
        durationMs: Long,
        onData: (List<DataPoint>) -> Unit
    ) {
        val deadline = System.currentTimeMillis() + durationMs

        withTimeoutOrNull(durationMs + POST_EXPERIMENT_DELAY_MS) {
            codec.parsedDataFlow
                .takeWhile { System.currentTimeMillis() < deadline }
                .collect { points -> onData(points) }
        }
    }

    private suspend fun sendStopSampling() {
        try {
            usbManager.write(codec.buildStopSamplingCmd())
            delay(POST_EXPERIMENT_DELAY_MS)
        } catch (e: Exception) {
            Log.w(TAG, "发送停止命令失败", e)
            DiagnosticLog.add(TAG, "stop sampling failed: ${e.message}")
        }
    }

    private fun fmt(value: Double): String {
        return String.format(Locale.US, "%.3f", value)
    }

    private fun fmtScientific(value: Double): String {
        return String.format(Locale.US, "%.6e", value)
    }
}
