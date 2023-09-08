/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.shape.MaterialShapeDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.recyclerview.ThumbnailAdapter
import org.lineageos.glimpse.recyclerview.ThumbnailLayoutManager
import org.lineageos.glimpse.utils.MediaStoreBuckets
import org.lineageos.glimpse.utils.PermissionsGatedCallback
import org.lineageos.glimpse.viewmodels.MediaViewModel

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumFragment : Fragment(R.layout.fragment_album) {
    // View models
    private val mediaViewModel: MediaViewModel by viewModels {
        MediaViewModel.factory(lifecycleScope, album.id)
    }

    // Views
    private val albumRecyclerView by getViewProperty<RecyclerView>(R.id.albumRecyclerView)
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsGatedCallback = PermissionsGatedCallback(this) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mediaViewModel.media.collectLatest {
                    thumbnailAdapter.data = it.toTypedArray()
                }
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter(album.id != MediaStoreBuckets.MEDIA_STORE_BUCKET_TRASH.id) { media ->
            findNavController().navigate(
                R.id.action_albumFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(media, album.id)
            )
        }
    }

    // Arguments
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM_ID, Album::class)!! }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navController = findNavController()

        appBarLayout.statusBarForeground =
            MaterialShapeDrawable.createWithElevationOverlay(requireContext())

        toolbar.title = album.name

        val appBarConfiguration = AppBarConfiguration(navController.graph)
        toolbar.setupWithNavController(navController, appBarConfiguration)

        albumRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
        albumRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            albumRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            albumRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        permissionsGatedCallback.runAfterPermissionsCheck()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        albumRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }

    companion object {
        private const val KEY_ALBUM_ID = "album_id"

        fun createBundle(
            album: Album,
        ) = bundleOf(
            KEY_ALBUM_ID to album,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param album Album.
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance(
            album: Album,
        ) = AlbumFragment().apply {
            arguments = createBundle(album)
        }
    }
}
