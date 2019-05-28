/*
 * Copyright Soramitsu Co., Ltd. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.chainadapter.provider

interface LastReadBlockProvider {
    fun getLastBlockHeight(): Long
    fun saveLastBlockHeight(height: Long)
}
