package no.nav.k9.vaktmester

import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URL

// https://doc.nais.io/addons/leader-election/
internal object LeaderElection {
    internal fun isLeader(): Boolean {
        val electorPath = System.getenv("ELECTOR_PATH")

        return when (electorPath.isNullOrBlank()) {
            true -> {
                logger.info("Environment variabel ELECTOR_PATH er ikke satt").let { true }
            }
            false -> {
                val leader = JSONObject(URL(electorPath).readText()).getString("name")
                val hostname = InetAddress.getLocalHost().hostName
                logger.info("Leader=[$leader], Hostname=[$hostname]")
                return hostname == leader
            }
        }
    }

    private val logger = LoggerFactory.getLogger(LeaderElection::class.java)

}