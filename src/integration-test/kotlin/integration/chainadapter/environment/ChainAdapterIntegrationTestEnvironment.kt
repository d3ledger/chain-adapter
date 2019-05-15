package integration.chainadapter.environment

import com.d3.chainadapter.CHAIN_ADAPTER_SERVICE_NAME
import com.d3.chainadapter.adapter.ChainAdapter
import com.d3.chainadapter.config.ChainAdapterConfig
import com.d3.chainadapter.provider.FileBasedLastReadBlockProvider
import com.d3.chainadapter.provider.LastReadBlockProvider
import com.d3.commons.config.IrohaConfig
import com.d3.commons.config.IrohaCredentialConfig
import com.d3.commons.config.RMQConfig
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.IrohaChainListener
import com.d3.commons.sidechain.iroha.util.ModelUtil
import com.d3.commons.util.createPrettySingleThreadPool
import integration.chainadapter.helper.ChainAdapterConfigHelper
import io.grpc.ManagedChannelBuilder
import iroha.protocol.BlockOuterClass
import iroha.protocol.Commands
import iroha.protocol.Primitive
import jp.co.soramitsu.crypto.ed25519.Ed25519Sha3
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Transaction
import jp.co.soramitsu.iroha.java.TransactionStatusObserver
import jp.co.soramitsu.iroha.testcontainers.IrohaContainer
import jp.co.soramitsu.iroha.testcontainers.PeerConfig
import jp.co.soramitsu.iroha.testcontainers.detail.GenesisBlockBuilder
import org.testcontainers.containers.FixedHostPortGenericContainer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.ImageFromDockerfile
import java.io.Closeable
import java.io.File
import java.util.*
import kotlin.math.absoluteValue


private val random = Random()
const val DEFAULT_RMQ_PORT = 5672

/**
 * Chain adapter test environment
 */
class ChainAdapterIntegrationTestEnvironment : Closeable {

    private val peerKeyPair = Ed25519Sha3().generateKeypair()

    private val rmqKeyPair =
        ModelUtil.loadKeypair("deploy/iroha/keys/rmq@notary.pub", "deploy/iroha/keys/rmq@notary.priv").get()

    private val dummyClientKeyPair = Ed25519Sha3().generateKeypair()

    // Random dummy value
    private val dummyValue = random.nextInt().absoluteValue.toString()

    val chainAdapterConfigHelper = ChainAdapterConfigHelper()

    val irohaContainer = IrohaContainer().withPeerConfig(getPeerConfig())

    private val rmq =
        KGenericContainer("rabbitmq:3-management").withExposedPorts(DEFAULT_RMQ_PORT).withFixedExposedPort(
            DEFAULT_RMQ_PORT, DEFAULT_RMQ_PORT
        )
    val userDir = System.getProperty("user.dir")!!
    private val dockerfile = "$userDir/Dockerfile"
    private val jarFile = "$userDir/build/libs/chain-adapter-all.jar"

    /**
     * Creates chain adapter docker container based on DockerFile
     * @return container
     */
    fun createChainAdapterContainer(): KGenericContainerImage {
        return KGenericContainerImage(
            ImageFromDockerfile()
                .withFileFromFile(jarFile, File(jarFile))
                .withFileFromFile("Dockerfile", File(dockerfile))
                .withBuildArg("JAR_FILE", jarFile)
        ).withLogConsumer { outputFrame -> print(outputFrame.utf8String) }.withNetworkMode("host")
    }

    private val irohaAPI: IrohaAPI

    init {
        rmq.start()
        // I don't want to see nasty Iroha logs
        irohaContainer.withLogger(null)
        irohaContainer.start()
        irohaAPI = irohaContainer.api
    }

    /**
     * Returns Iroha peer config
     */
    private fun getPeerConfig(): PeerConfig {
        val config = PeerConfig.builder()
            .genesisBlock(getGenesisBlock())
            .build()
        config.withPeerKeyPair(peerKeyPair)
        return config
    }

    /**
     * Creates test genesis block
     */
    private fun getGenesisBlock(): BlockOuterClass.Block {
        return GenesisBlockBuilder().addTransaction(
            Transaction.builder("")
                .addPeer("0.0.0.0:10001", peerKeyPair.public)
                .createRole("none", emptyList())
                .createRole(
                    "client",
                    listOf(
                        Primitive.RolePermission.can_set_detail
                    )
                ).createRole(
                    "rmq", listOf(
                        Primitive.RolePermission.can_get_blocks
                    )
                )
                .createDomain("notary", "none")
                .createDomain("d3", "client")
                .createAccount("rmq@notary", rmqKeyPair.public)
                .createAccount("client@d3", dummyClientKeyPair.public)
                .appendRole("rmq@notary", "rmq")
                .build()
                .build()
        ).build()
    }

    /**
     * Creates ChainAdapter
     */
    fun createAdapter(): OpenChainAdapter {
        val chainAdapterConfig =
            chainAdapterConfigHelper.createChainAdapterConfig(
                rmq.containerIpAddress,
                rmq.getMappedPort(DEFAULT_RMQ_PORT)
            )
        val irohaAPI = irohaAPI()
        val lastReadBlockProvider = FileBasedLastReadBlockProvider(chainAdapterConfig)
        val queryAPI =
            QueryAPI(
                irohaAPI,
                chainAdapterConfig.irohaCredential.accountId,
                rmqKeyPair
            )
        val irohaChainListener = IrohaChainListener(
            irohaAPI,
            IrohaCredential(chainAdapterConfig.irohaCredential.accountId, rmqKeyPair)
        )
        return OpenChainAdapter(
            chainAdapterConfig,
            queryAPI,
            irohaChainListener,
            lastReadBlockProvider
        )
    }

    /**
     * It's essential to handle blocks in this service one-by-one.
     * This is why we explicitly set single threaded executor.
     */
    private fun irohaAPI(): IrohaAPI {
        val api = irohaContainer.api
        val irohaAddress = irohaContainer.toriiAddress
        api.setChannelForStreamingQueryStub(
            ManagedChannelBuilder.forAddress(
                irohaAddress.host, irohaAddress.port
            ).executor(
                createPrettySingleThreadPool(
                    CHAIN_ADAPTER_SERVICE_NAME,
                    "iroha-chain-listener"
                )
            ).usePlaintext().build()
        )
        return api
    }

    /**
     * Creates dummy transaction
     */
    fun createDummyTransaction(testKey: String = dummyValue) {
        val transactionBuilder = Transaction
            .builder("client@d3")
            .setAccountDetail("client@d3", testKey, dummyValue)
            .sign(dummyClientKeyPair)
        irohaAPI.transaction(transactionBuilder.build())
            .blockingSubscribe(
                TransactionStatusObserver.builder()
                    .onError { ex -> throw ex }
                    .onTransactionFailed { tx -> throw Exception("${tx.txHash} has failed") }
                    .onRejected { tx -> throw Exception("${tx.txHash} has been rejected") }
                    .build()
            )

    }

    /**
     * Checks if command is dummy
     */
    fun isDummyCommand(command: Commands.Command): Boolean {
        return command.hasSetAccountDetail() && command.setAccountDetail.value == dummyValue
    }

    /**
     * Maps ChainAdapterConfig to RMQConfig
     * @param chainAdapterConfig - config to map
     * @return RMQConfig based on [chainAdapterConfig]
     */
    fun mapToRMQConfig(chainAdapterConfig: ChainAdapterConfig): RMQConfig {
        return object : RMQConfig {
            override val host = chainAdapterConfig.rmqHost
            override val iroha = chainAdapterConfig.iroha
            override val irohaCredential = chainAdapterConfig.irohaCredential
            override val irohaExchange = chainAdapterConfig.irohaExchange
            override val lastReadBlockFilePath = chainAdapterConfig.lastReadBlockFilePath
        }
    }

    override fun close() {
        irohaAPI.close()
        irohaContainer.close()
        rmq.close()
    }
}

/**
 * The GenericContainer class is not very friendly to Kotlin.
 * So the following class was created as a workaround.
 */
class KGenericContainer(imageName: String) : FixedHostPortGenericContainer<KGenericContainer>(imageName)

class KGenericContainerImage(image: ImageFromDockerfile) : GenericContainer<KGenericContainerImage>(image)

/**
 * This ChainAdapter implementation is used to expose values
 * that are private in the original class
 */
class OpenChainAdapter(
    val chainAdapterConfig: ChainAdapterConfig,
    queryAPI: QueryAPI,
    irohaChainListener: IrohaChainListener,
    val lastReadBlockProvider: LastReadBlockProvider
) : ChainAdapter(chainAdapterConfig, queryAPI, irohaChainListener, lastReadBlockProvider)
