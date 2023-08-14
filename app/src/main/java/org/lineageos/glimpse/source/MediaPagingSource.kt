package org.lineageos.glimpse.source

import android.content.ContentResolver
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.os.bundleOf
import androidx.paging.PagingSource
import androidx.paging.PagingState
import kotlinx.coroutines.flow.collectLatest
import org.lineageos.glimpse.ext.mapEachRow
import org.lineageos.glimpse.ext.uriFlow
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.utils.MediaStoreBuckets

class MediaPagingSource(
    private val contentResolver: ContentResolver,
    private val bucketId: Int?
) : PagingSource<Int, Media>() {

    override fun getRefreshKey(state: PagingState<Int, Media>) =
        state.anchorPosition?.let { anchorPosition ->
            val anchorPageIndex = state.pages.indexOf(state.closestPageToPosition(anchorPosition))
            state.pages.getOrNull(anchorPageIndex + 1)?.prevKey ?: state.pages.getOrNull(
                anchorPageIndex - 1
            )?.nextKey
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Media> {
        val offset = params.key ?: 0
        val limit = params.loadSize

        val uri = MediaQuery.MediaStoreFileUri
        val projection = MediaQuery.MediaProjection
        val imageOrVideo =
            (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) or
                    (MediaStore.Files.FileColumns.MEDIA_TYPE eq MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val albumFilter = bucketId?.let {
            when (it) {
                MediaStoreBuckets.MEDIA_STORE_BUCKET_FAVORITES.id -> MediaStore.Files.FileColumns.IS_FAVORITE eq 1

                MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    MediaStore.Files.FileColumns.IS_TRASHED eq 1
                } else {
                    null
                }

                else -> MediaStore.Files.FileColumns.BUCKET_ID eq Query.ARG
            }
        }
        val selection = albumFilter?.let { imageOrVideo and it } ?: imageOrVideo
        val selectionArgs = bucketId?.takeIf {
            MediaStoreBuckets.values().none { bucket -> it == bucket.id }
        }?.let { arrayOf(it.toString()) }
        val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        val queryArgs = Bundle().apply {
            putAll(
                bundleOf(
                    ContentResolver.QUERY_ARG_SQL_SELECTION to selection.build(),
                    ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS to selectionArgs,
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER to sortOrder,
                    ContentResolver.QUERY_ARG_LIMIT to limit,
                    ContentResolver.QUERY_ARG_OFFSET to offset,
                )
            )
            // Exclude trashed media unless we want data for the trashed album
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED, when (bucketId) {
                        MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id -> MediaStore.MATCH_ONLY

                        else -> MediaStore.MATCH_EXCLUDE
                    }
                )
            }
        }

        val cursor = contentResolver.query(
            uri,
            projection,
            queryArgs,
            null,
        )

        val data = mediaFromCursor(cursor)
        val prevKey = null
        val nextKey = if (data.isNotEmpty()) offset + data.size else null

        println("MERDA size ${data.size}, prev $prevKey, next $nextKey, limit ${params.loadSize} offset ${(params.key ?: 0)}")

        return LoadResult.Page(
            data = data,
            prevKey = prevKey,
            nextKey = nextKey,
        )
    }

    private fun mediaFromCursor(cursor: Cursor?) =
        cursor.mapEachRow {
            val idIndex = it.getColumnIndex(MediaStore.Files.FileColumns._ID)
            val bucketIdIndex = it.getColumnIndex(MediaStore.MediaColumns.BUCKET_ID)
            val isFavoriteIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_FAVORITE)
            val isTrashedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.IS_TRASHED)
            val mediaTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val mimeTypeIndex = it.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateAddedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
            val dateModifiedIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)
            val orientationIndex = it.getColumnIndex(MediaStore.Files.FileColumns.ORIENTATION)

            val id = it.getLong(idIndex)
            val buckedId = it.getInt(bucketIdIndex)
            val isFavorite = it.getInt(isFavoriteIndex)
            val isTrashed = it.getInt(isTrashedIndex)
            val mediaType = it.getInt(mediaTypeIndex)
            val mimeType = it.getString(mimeTypeIndex)
            val dateAdded = it.getLong(dateAddedIndex)
            val dateModified = it.getLong(dateModifiedIndex)
            val orientation = it.getInt(orientationIndex)

            Media.fromMediaStore(
                id,
                buckedId,
                isFavorite,
                isTrashed,
                mediaType,
                mimeType,
                dateAdded,
                dateModified,
                orientation,
            )
        }
}
