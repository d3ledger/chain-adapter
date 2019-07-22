/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

@file:JvmName("ChainAdapterMain")

package com.d3.chainadapter

import com.d3.chainadapter.adapter.ChainAdapter
import com.d3.chainadapter.config.ChainAdapterConfig
import com.d3.commons.config.loadRawLocalConfigs
import com.d3.commons.model.IrohaCredential
import com.d3.commons.sidechain.iroha.IrohaChainListener
import com.d3.commons.sidechain.iroha.util.impl.IrohaQueryHelperImpl
import com.d3.commons.sidechain.provider.FileBasedLastReadBlockProvider
import com.d3.commons.util.createPrettySingleThreadPool
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.map
import io.grpc.ManagedChannelBuilder
import jp.co.soramitsu.iroha.java.IrohaAPI
import jp.co.soramitsu.iroha.java.QueryAPI
import jp.co.soramitsu.iroha.java.Utils
import mu.KLogging
import java.io.File
import java.io.IOException

private val logger = KLogging().logger
const val CHAIN_ADAPTER_SERVICE_NAME = "chain-adapter"

//TODO Springify
fun main(args: Array<String>) {
    val chainAdapterConfig =
        loadRawLocalConfigs(
            "chain-adapter",
            ChainAdapterConfig::class.java,
            "chain-adapter.properties"
        )

    val irohaCredential = chainAdapterConfig.irohaCredential
    Result.of { Utils.parseHexKeypair(irohaCredential.pubkey, irohaCredential.privkey) }
        .map { keyPair ->
            createLastReadBlockFile(chainAdapterConfig)

            /**
             * It's essential to handle blocks in this service one-by-one.
             * This is why we explicitly set single threaded executor.
             */
            val irohaAPI =
                IrohaAPI(chainAdapterConfig.iroha.hostname, chainAdapterConfig.iroha.port)
            irohaAPI.setChannelForStreamingQueryStub(
                ManagedChannelBuilder.forAddress(
                    chainAdapterConfig.iroha.hostname, chainAdapterConfig.iroha.port
                ).executor(
                    createPrettySingleThreadPool(
                        CHAIN_ADAPTER_SERVICE_NAME,
                        "iroha-chain-listener"
                    )
                ).usePlaintext().build()
            )
            val queryAPI =
                QueryAPI(
                    irohaAPI,
                    irohaCredential.accountId,
                    keyPair
                )
            val irohaChainListener = IrohaChainListener(
                irohaAPI,
                IrohaCredential(irohaCredential.accountId, keyPair)
            )
            val adapter = ChainAdapter(
                chainAdapterConfig,
                IrohaQueryHelperImpl(queryAPI),
                irohaChainListener,
                FileBasedLastReadBlockProvider(chainAdapterConfig.lastReadBlockFilePath)
            )
            adapter.init {
                logger.error("Iroha failure. Exit.")
                System.exit(1)
            }.fold(
                { logger.info("Chain adapter has been started") },
                { ex ->
                    adapter.close()
                    throw ex
                })
        }.failure { ex ->
        logger.error("Cannot start chain-adapter", ex)
        System.exit(1)
    }
}

/**
 * Creates last read block file
 * @param chainAdapterConfig - ChainAdapterConfig config
 */
private fun createLastReadBlockFile(chainAdapterConfig: ChainAdapterConfig) {
    val file = File(chainAdapterConfig.lastReadBlockFilePath)
    if (file.exists()) {
        //No need to create
        return
    }
    val folder = File(file.parentFile.absolutePath)
    if (!folder.exists() && !folder.mkdirs()) {
        throw IOException("Cannot create folder")
    } else if (!file.createNewFile()) {
        throw IOException("Cannot create file for last read block storage")
    }
}
