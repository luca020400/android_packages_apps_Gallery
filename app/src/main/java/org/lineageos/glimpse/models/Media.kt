/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.models

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.signature.MediaStoreSignature
import java.util.Date
import kotlin.reflect.safeCast

data class Media(
    val id: Long,
    val bucketId: Int,
    val isFavorite: Boolean,
    val isTrashed: Boolean,
    val mediaType: MediaType,
    val mimeType: String,
    val dateAdded: Date,
    val dateModified: Date,
    val orientation: Int
) : Parcelable, Comparable<Media> {
    val externalContentUri = ContentUris.withAppendedId(mediaType.externalContentUri, id)

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readInt(),
        parcel.readInt() == 1,
        parcel.readInt() == 1,
        when (parcel.readInt()) {
            MediaType.IMAGE.ordinal -> MediaType.IMAGE
            MediaType.VIDEO.ordinal -> MediaType.VIDEO
            else -> throw Exception("Invalid media type")
        },
        parcel.readString()!!,
        Date(parcel.readLong()),
        Date(parcel.readLong()),
        parcel.readInt(),
    )

    override fun describeContents() = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeInt(bucketId)
        dest.writeInt(if (isFavorite) 1 else 0)
        dest.writeInt(if (isTrashed) 1 else 0)
        dest.writeInt(mediaType.ordinal)
        dest.writeString(mimeType)
        dest.writeLong(dateAdded.time)
        dest.writeLong(dateModified.time)
        dest.writeInt(orientation)
    }

    override fun compareTo(other: Media) = compareValuesBy(
        this, other,
        { it.id },
        { it.externalContentUri },
        { it.bucketId },
        { it.isFavorite },
        { it.isTrashed },
        { it.mediaType },
        { it.mimeType },
        { it.dateAdded },
        { it.dateModified },
        { it.orientation },
    )

    override fun equals(other: Any?): Boolean {
        val obj = Media::class.safeCast(other) ?: return false
        return compareTo(obj) == 0
    }

    fun delete(contentResolver: ContentResolver) {
        contentResolver.delete(externalContentUri, null, null)
    }

    fun favorite(contentResolver: ContentResolver, value: Boolean) {
        contentResolver.update(externalContentUri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_FAVORITE, value)
        }, null, null)
    }

    fun trash(contentResolver: ContentResolver, value: Boolean) {
        contentResolver.update(externalContentUri, ContentValues().apply {
            put(MediaStore.MediaColumns.IS_TRASHED, value)
        }, null, null)
    }

    fun signature() = MediaStoreSignature(mimeType, dateModified.time * 1000, orientation)

    companion object CREATOR : Parcelable.Creator<Media> {
        val comparator = object : DiffUtil.ItemCallback<Media>() {
            override fun areItemsTheSame(oldItem: Media, newItem: Media) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Media, newItem: Media) =
                oldItem.compareTo(newItem) == 0
        }

        override fun createFromParcel(parcel: Parcel) = Media(parcel)

        override fun newArray(size: Int) = arrayOfNulls<Media>(size)

        fun fromMediaStore(
            id: Long,
            bucketId: Int,
            isFavorite: Int,
            isTrashed: Int,
            mediaType: Int,
            mimeType: String,
            dateAdded: Long,
            dateModified: Long,
            orientation: Int,
        ) = Media(
            id,
            bucketId,
            isFavorite == 1,
            isTrashed == 1,
            MediaType.fromMediaStoreValue(mediaType),
            mimeType,
            Date(dateAdded * 1000),
            Date(dateModified * 1000),
            orientation,
        )
    }
}
