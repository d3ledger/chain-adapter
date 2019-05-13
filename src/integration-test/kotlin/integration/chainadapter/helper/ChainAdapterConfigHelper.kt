package integration.chainadapter.helper

import com.d3.commons.config.RMQConfig
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
     * Creates RabbitMQ config
     */
    fun createRmqConfig(rmqHost:String): RMQConfig {
        val rmqConfig = loadRawConfigs("rmq", RMQConfig::class.java, "${getConfigFolder()}/rmq.properties")
        return object : RMQConfig {
            override val host = rmqHost
            // Random exchange
            override val irohaExchange = random.nextInt().absoluteValue.toString()
            override val irohaCredential = rmqConfig.irohaCredential
            override val iroha = rmqConfig.iroha
            override val lastReadBlockFilePath = createTestLastReadBlockFile()
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
