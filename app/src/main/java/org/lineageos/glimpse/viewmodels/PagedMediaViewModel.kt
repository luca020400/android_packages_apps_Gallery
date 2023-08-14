package org.lineageos.glimpse.viewmodels

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.source.MediaPagingSource

class PagedMediaViewModel(
    private val contentResolver: ContentResolver, private val bucketId: Int?
) : ViewModel() {
    private val pager = Pager(
        config = PagingConfig(enablePlaceholders = false, initialLoadSize = 1, pageSize = 1),
        pagingSourceFactory = {
            MediaPagingSource(contentResolver, bucketId)
        },
    )

    val pagingFlow = pager.flow.cachedIn(viewModelScope)

    companion object {
        fun factory(bucketId: Int? = null) = viewModelFactory {
            initializer {
                PagedMediaViewModel(
                    contentResolver = (this[AndroidViewModelFactory.APPLICATION_KEY] as GlimpseApplication).contentResolver,
                    bucketId = bucketId,
                )
            }
        }
    }
}