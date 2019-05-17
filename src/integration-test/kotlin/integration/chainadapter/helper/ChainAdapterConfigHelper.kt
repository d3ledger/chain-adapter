package integration.chainadapter.helper

import com.d3.chainadapter.config.ChainAdapterConfig
import com.d3.commons.config.getConfigFolder
import com.d3.commons.config.loadRawConfigs
import java.io.File
import java.io.IOException
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
        val chainAdapterConfig = loadRawConfigs(
            "chain-adapter",
            ChainAdapterConfig::class.java,
            "${getConfigFolder()}/chain-adapter.properties"
        )
        return object : ChainAdapterConfig {
            override val rmqHost = rmqHost
            override val rmqPort = rmqPort
            // Random exchange
            override val irohaExchange = random.nextInt().absoluteValue.toString()
            override val irohaCredential = chainAdapterConfig.irohaCredential
            override val iroha = chainAdapterConfig.iroha
            override val lastReadBlockFilePath = createTestLastReadBlockFile()
            override val dropLastReadBlock = chainAdapterConfig.dropLastReadBlock
        }
    }

    /**
     * Creates randomly named file for last read block height storage
     */
    fun createTestLastReadBlockFile(): String {
        // Random file
        val file = File("$LAST_READ_BLOCK_TEST_FOLDER/last_block_${random.nextInt().absoluteValue}.txt")
        val folder = File(file.parentFile.absolutePath)
        if (!folder.exists() && !folder.mkdirs()) {
            throw IOException("Cannot create chain-adapter test folder")
        }
        if (!file.createNewFile()) {
            throw IOException("Cannot create file for last read block storage")
        }
        return file.absolutePath
    }
}
