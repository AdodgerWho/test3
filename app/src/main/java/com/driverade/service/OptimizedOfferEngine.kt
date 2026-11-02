package com.driverade.service

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class OptimizedOfferEngine(
    private val targetHourlyRate: Double,
    private val minPayout: Double
) {
    companion object {
        private const val TAG = "OptimizedOfferEngine"
    }

    // Smart State Cache (Diffing) to prevent redundant redraws
    private var lastCachedFare: Double = -1.0
    private var lastCachedRate: Double = -1.0

    data class Metrics(var fare: Double = 0.0, var hourlyRate: Double = 0.0)

    fun evaluateNode(rootNode: AccessibilityNodeInfo): Metrics? {
        val metrics = findAndParseOffer(rootNode) ?: return null

        // Diffing check: Skip update if metrics haven't changed
        if (metrics.fare == lastCachedFare && metrics.hourlyRate == lastCachedRate) {
            return null // No change, avoid redundant UI updates
        }

        lastCachedFare = metrics.fare
        lastCachedRate = metrics.hourlyRate
        return metrics
    }

    private fun findAndParseOffer(node: AccessibilityNodeInfo): Metrics? {
        // Targeted pruning with early exit
        fun traverse(current: AccessibilityNodeInfo): Metrics? {
            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""

            if ((text.contains("$") || desc.contains("$")) &&
                (text.contains("min") || text.contains("mi") || text.contains("/hr"))) {
                
                val metrics = Metrics()
                parseMetrics(current, metrics)
                if (metrics.fare > 0.0 || metrics.hourlyRate > 0.0) {
                    return metrics // Early exit on first valid match
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { child ->
                    val result = traverse(child)
                    if (result != null) return result
                }
            }
            return null
        }

        return traverse(node)
    }

    private fun parseMetrics(cardNode: AccessibilityNodeInfo, metrics: Metrics) {
        fun extract(current: AccessibilityNodeInfo) {
            val text = current.text?.toString() ?: current.contentDescription?.toString() ?: ""
            if (text.contains("$") && metrics.fare == 0.0) {
                text.replace("$", "").trim().toDoubleOrNull()?.let { metrics.fare = it }
            }
            if (text.contains("/hr", ignoreCase = true)) {
                text.replace(Regex("[^\d.]"), "").toDoubleOrNull()?.let { metrics.hourlyRate = it }
            }
            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { extract(it) }
            }
        }
        extract(cardNode)
    }
}
