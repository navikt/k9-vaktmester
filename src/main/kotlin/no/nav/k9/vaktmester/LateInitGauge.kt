package no.nav.k9.vaktmester

import io.prometheus.client.Gauge

internal class LateInitGauge(
    private val gaugeBuilder: Gauge.Builder) {
    private lateinit var gauge: Gauge
    private fun isInitialized() = ::gauge.isInitialized
    private fun ensureInitialized() {
        if (isInitialized()) return
        else gauge = gaugeBuilder.register()
    }

    internal fun inc(vararg labelValues: String) = ensureInitialized().also {
        gauge.labels(*labelValues).inc()
    }

    internal fun setToCurrentTime() = ensureInitialized().also {
        gauge.setToCurrentTime()
    }

    internal fun clear() = isInitialized().let { if (it) {
        gauge.clear()
    }}
}