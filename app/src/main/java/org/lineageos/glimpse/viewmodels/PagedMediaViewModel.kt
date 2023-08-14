package org.lineageos.glimpse.viewmodels

import android.content.ContentResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.source.MediaPagingSource

class PagedMediaViewModel(
    private val contentResolver: ContentResolver
) : ViewModel() {
    private var dataSource: MediaPagingSource? = null
    private val pager = Pager(
        config = PagingConfig(enablePlaceholders = false, initialLoadSize = 1, pageSize = 1),
        pagingSourceFactory = {
            MediaPagingSource(
                contentResolver,
                bucketId.value
            ).also { dataSource = it }
        },
    )

    private val bucketId = MutableStateFlow<Int?>(null)
    fun setBucketId(bucketId: Int?) {
        this.bucketId.value = bucketId
        dataSource?.invalidate()
    }

    val pagingFlow = pager.flow.cachedIn(viewModelScope)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                PagedMediaViewModel(
                    contentResolver = (this[AndroidViewModelFactory.APPLICATION_KEY] as GlimpseApplication).contentResolver,
                )
            }
        }
    }
}