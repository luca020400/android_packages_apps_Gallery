/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.repository

import android.content.Context

class MediaRepository(private val context: Context) {
    fun media(bucketId: Int? = null) = MediaFlow(context, bucketId).flow()
    fun albums() = AlbumsFlow(context).flow()
}
