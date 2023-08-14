package org.lineageos.glimpse.viewmodels

import android.content.ContentResolver
import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.GlimpseApplication
import org.lineageos.glimpse.ext.uriFlow
import org.lineageos.glimpse.query.*
import org.lineageos.glimpse.source.MediaPagingSource

class PagedMediaViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val contentResolver: ContentResolver,
    private val bucketId: Int?
) : ViewModel() {
    private val mediaPositionInternal = savedStateHandle.getLiveData<Int>(MEDIA_POSITION_KEY)
    val mediaPositionLiveData: LiveData<Int> = mediaPositionInternal
    var mediaPosition: Int
        set(value) {
            mediaPositionInternal.value = value
        }
        get() = mediaPositionInternal.value!!

    private var dataSource: MediaPagingSource? = null
    private val pager = Pager(
        config = PagingConfig(enablePlaceholders = false, initialLoadSize = 100, pageSize = 20),
        pagingSourceFactory = {
            MediaPagingSource(contentResolver, bucketId).also { dataSource = it }
        },
    )

    val pagingFlow = pager.flow.cachedIn(viewModelScope)

    init {
        val uri = MediaQuery.MediaStoreFileUri
        viewModelScope.launch {
            contentResolver.uriFlow(uri).collectLatest {
                dataSource?.invalidate()
            }
        }
    }

    companion object {
        private const val MEDIA_POSITION_KEY = "position"

        fun factory(bucketId: Int? = null) = viewModelFactory {
            initializer {
                PagedMediaViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    contentResolver = (this[AndroidViewModelFactory.APPLICATION_KEY] as GlimpseApplication).contentResolver,
                    bucketId = bucketId,
                )
            }
        }
    }
}