package integration.chainadapter.helper

import com.d3.chainadapter.config.ChainAdapterConfig
import com.d3.commons.config.loadRawLocalConfigs
import java.util.*
import kotlin.math.absoluteValue

// Folder for chain-adapter test files(last read block file and etc)
private const val LAST_READ_BLOCK_TEST_FOLDER = "deploy/chain-adapter/tests"

private val random = Random()

class ChainAdapterConfigHelper {

    /**
     * Creates chain adapter config
     */
    fun createChainAdapterConfig(rmqHost: String, rmqPort: Int): ChainAdapterConfig {
        val chainAdapterConfig = loadRawLocalConfigs(
            "chain-adapter",
            ChainAdapterConfig::class.java,
            "chain-adapter.properties"
        )
        return object : ChainAdapterConfig {
            override val rmqHost = rmqHost
            override val rmqPort = rmqPort
            // Random exchange
            override val irohaExchange = random.nextInt().absoluteValue.toString()
            override val irohaCredential = chainAdapterConfig.irohaCredential
            override val iroha = chainAdapterConfig.iroha
            override val lastReadBlockFilePath = chainAdapterConfig.lastReadBlockFilePath
            override val dropLastReadBlock = true
        }
    }
}
