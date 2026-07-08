package com.example.eprotocol.domain

import com.example.eprotocol.data.model.RegressionResult
import org.apache.commons.math3.stat.regression.SimpleRegression
import kotlin.math.abs

/**
 * 数学计算工具类
 *
 * 提供均值计算、线性回归拟合、R^2 判定以及异常点剔除等功能。
 * 拟合坐标约定: X = 电流, Y = 电压。
 * 拟合直线的 Y 轴截距即为电流为零时的平衡电位。
 */
object MathUtils {

    private const val MIN_POINTS_FOR_FIT = 5
    private const val R_SQUARED_THRESHOLD = 0.9

    fun average(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sum() / values.size
    }

    /**
     * 对 (电流, 电压) 数据对进行线性回归拟合。
     *
     * 当 X 值 (电流) 全部相同或方差为零时，SimpleRegression 会产生 NaN。
     * 此处做防护：X 不足 2 个不同值时返回 rSquared=0 的降级结果。
     */
    fun linearRegression(points: List<Pair<Double, Double>>): RegressionResult {
        require(points.size >= 2) { "线性拟合至少需要2个数据点，当前只有 ${points.size} 个" }

        // 零方差防护: X 值全部相同时无法拟合有意义的直线
        val distinctXCount = points.map { it.first }.distinct().size
        if (distinctXCount < 2) {
            return RegressionResult(
                slope = 0.0,
                intercept = average(points.map { it.second }),
                rSquared = 0.0
            )
        }

        val regression = SimpleRegression()
        for ((current, voltage) in points) {
            regression.addData(current, voltage)
        }

        val rSq = regression.rSquare
        return RegressionResult(
            slope = regression.slope,
            intercept = regression.intercept,
            rSquared = if (rSq.isNaN() || rSq.isInfinite()) 0.0 else rSq
        )
    }

    fun removeWorstOutlier(
        points: List<Pair<Double, Double>>,
        regression: RegressionResult
    ): List<Pair<Double, Double>> {
        if (points.size <= MIN_POINTS_FOR_FIT) return points

        var worstIdx = 0
        var worstResidual = 0.0

        for (i in points.indices) {
            val (current, voltage) = points[i]
            val predicted = regression.slope * current + regression.intercept
            val residual = abs(voltage - predicted)
            if (residual > worstResidual) {
                worstResidual = residual
                worstIdx = i
            }
        }

        return points.toMutableList().apply { removeAt(worstIdx) }
    }

    /**
     * 线性拟合 + 异常点自动剔除循环。
     *
     * NaN/Infinite 的 R^2 视为不合格，会触发剔除流程。
     */
    fun fitWithOutlierRemoval(points: List<Pair<Double, Double>>): RegressionResult {
        var currentPoints = points.toList()
        var result = linearRegression(currentPoints)

        while ((result.rSquared < R_SQUARED_THRESHOLD || result.rSquared.isNaN())
            && currentPoints.size > MIN_POINTS_FOR_FIT
        ) {
            currentPoints = removeWorstOutlier(currentPoints, result)
            result = linearRegression(currentPoints)
        }

        return result
    }
}
