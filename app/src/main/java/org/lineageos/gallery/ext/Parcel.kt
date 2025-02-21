/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.gallery.ext

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import kotlin.reflect.KClass

fun <T : Parcelable> Parcel.readParcelable(clazz: KClass<T>) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        readParcelable((clazz.java::getClassLoader)(), clazz.java)
    } else {
        @Suppress("DEPRECATION")
        readParcelable((clazz.java::getClassLoader)())
    }
