/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.FragmentNavigatorExtras
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
import org.lineageos.glimpse.ext.getParcelable
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.thumbnail.ThumbnailPagingAdapter
import org.lineageos.glimpse.thumbnail.ThumbnailPagingLayoutManager
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.PagedMediaViewModel

/**
 * A fragment showing a list of media from a specific album with thumbnails.
 * Use the [AlbumFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class AlbumFragment : Fragment(R.layout.fragment_album) {
    // View models
    private val pagedMediaViewModel: PagedMediaViewModel by viewModels {
        PagedMediaViewModel.factory(
            album.id
        )
    }

    // Views
    private val albumRecyclerView by getViewProperty<RecyclerView>(R.id.albumRecyclerView)
    private val appBarLayout by getViewProperty<AppBarLayout>(R.id.appBarLayout)
    private val toolbar by getViewProperty<MaterialToolbar>(R.id.toolbar)

    // Permissions
    private val permissionsUtils by lazy { PermissionsUtils(requireContext()) }
    private val mainPermissionsRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (!permissionsUtils.mainPermissionsGranted()) {
                Toast.makeText(
                    requireContext(), R.string.app_permissions_toast, Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    pagedMediaViewModel.pagingFlow.collectLatest { pagingData ->
                        thumbnailAdapter.submitData(pagingData)
                    }
                }
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailPagingAdapter { media, position, anchor ->
            val extras = FragmentNavigatorExtras(
                anchor to "${media.id}"
            )
            findNavController().navigate(
                R.id.action_albumFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(
                    album, media, position
                ),
                null,
                extras
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

        albumRecyclerView.layoutManager = ThumbnailPagingLayoutManager(
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

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            viewLifecycleOwner.lifecycleScope.launch {
                pagedMediaViewModel.pagingFlow.collectLatest { pagingData ->
                    thumbnailAdapter.submitData(pagingData)
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        albumRecyclerView.layoutManager = ThumbnailPagingLayoutManager(
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
