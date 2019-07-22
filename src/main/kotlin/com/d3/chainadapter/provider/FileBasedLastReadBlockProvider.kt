/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.chainadapter.provider

import com.d3.chainadapter.config.ChainAdapterConfig
import org.springframework.stereotype.Component
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

/**
 * File based last processed Iroha block reader
 */
@Component
class FileBasedLastReadBlockProvider(private val chainAdapterConfig: ChainAdapterConfig) :
    LastReadBlockProvider {

    init {
        createLastReadBlockFile(chainAdapterConfig)
    }

    /**
     * Returns last processed block
     * Value is read from file
     */
    @Synchronized
    override fun getLastBlockHeight(): Long {
        Scanner(File(chainAdapterConfig.lastReadBlockFilePath)).use { scanner ->
            return if (scanner.hasNextLine()) {
                scanner.next().toLong()
            } else {
                0
            }
        }
    }

    /**
     * Save last block height in file
     * @param height - height of block that will be saved
     */
    @Synchronized
    override fun saveLastBlockHeight(height: Long) {
        FileWriter(File(chainAdapterConfig.lastReadBlockFilePath)).use { fileWriter ->
            BufferedWriter(fileWriter).use { writer ->
                writer.write(height.toString())
            }
        }
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
