package no.nav.k9.vaktmester

import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.net.URL

// https://doc.nais.io/addons/leader-election/
internal object LeaderElection {

    @JvmStatic
    internal val isLeader: Boolean = {
        val logger = LoggerFactory.getLogger(LeaderElection::class.java)

        val electorPath = System.getenv("ELECTOR_PATH")

        logger.info("ElectorPath=[$electorPath]")

        when (electorPath.isNullOrBlank()) {
            true -> {
                logger.info("Environment variabel ELECTOR_PATH er ikke satt").let { true }
            }
            false -> {
                val leader = JSONObject(URL("http://$electorPath").readText()).getString("name")
                val hostname = InetAddress.getLocalHost().hostName
                val isLeader = hostname == leader
                logger.info("Leader=[$leader], Hostname=[$hostname], IsLeader=[$isLeader]")
                isLeader
            }
        }
    }()

}