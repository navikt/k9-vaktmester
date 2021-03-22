package no.nav.k9.vaktmester

import org.slf4j.LoggerFactory
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val zoneId = ZoneId.of("Europe/Oslo")

internal class Arbeidstider(private val nå: () -> LocalDateTime = {
    LocalDateTime.now(zoneId)
}) {
    private val logger = LoggerFactory.getLogger(Arbeidstider::class.java)

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private fun LocalTime.formater() = format(dateTimeFormatter)

    private fun Pair<LocalTime, LocalTime>.inneholder(localTime: LocalTime) =
        localTime.isAfter(first) && localTime.isBefore(second)

    internal fun skalRepublisereNå() = nå().let { nå ->
        val dag = nå.dayOfWeek
        val tidspunkt = nå.toLocalTime()
        val periode = perioder.getValue(dag)
        periode.inneholder(tidspunkt).also { if (!it) {
            logger.info("Utenfor periode for republisering. [${dag.name}@${tidspunkt.formater()} != <${periode.first.formater()},${periode.second.formater()}>]")
        }}
    }

    private val hverdag =
        LocalTime.parse("08:00:00") to LocalTime.parse("19:00:00")

    private val helg =
        LocalTime.parse("12:00:00") to LocalTime.parse("13:00:00")

    private val perioder = mapOf(
        DayOfWeek.MONDAY to hverdag,
        DayOfWeek.TUESDAY to hverdag,
        DayOfWeek.WEDNESDAY to hverdag,
        DayOfWeek.THURSDAY to hverdag,
        DayOfWeek.FRIDAY to hverdag,
        DayOfWeek.SATURDAY to helg,
        DayOfWeek.SUNDAY to helg
    )
}