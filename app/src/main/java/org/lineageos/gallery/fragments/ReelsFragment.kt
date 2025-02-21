/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.gallery.fragments

import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import org.lineageos.gallery.R
import org.lineageos.gallery.ext.getViewProperty
import org.lineageos.gallery.thumbnail.ThumbnailAdapter
import org.lineageos.gallery.thumbnail.ThumbnailLayoutManager
import org.lineageos.gallery.utils.CommonNavigationArguments
import org.lineageos.gallery.utils.MediaStoreRequests
import org.lineageos.gallery.utils.PermissionsUtils

/**
 * A fragment showing a list of media with thumbnails.
 * Use the [ReelsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReelsFragment : Fragment(R.layout.fragment_reels), LoaderManager.LoaderCallbacks<Cursor> {
    // Views
    private val reelsRecyclerView by getViewProperty<RecyclerView>(R.id.reelsRecyclerView)

    // Fragments
    private val parentNavController by lazy {
        requireParentFragment().requireParentFragment().findNavController()
    }

    // Permissions
    private val permissionsUtils by lazy { PermissionsUtils(requireContext()) }
    private val mainPermissionsRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (it.isNotEmpty()) {
            if (!permissionsUtils.mainPermissionsGranted()) {
                Toast.makeText(
                    requireContext(), "No main permissions", Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            } else {
                initCursorLoader()
            }
        }
    }

    // MediaStore
    private val loaderManagerInstance by lazy { LoaderManager.getInstance(this) }
    private val thumbnailAdapter by lazy {
        ThumbnailAdapter { media, position ->
            parentNavController.navigate(
                R.id.action_mainFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(
                    null, media, position
                )
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reelsRecyclerView.layoutManager = ThumbnailLayoutManager(
            requireContext(), thumbnailAdapter, 3
        )
        reelsRecyclerView.adapter = thumbnailAdapter

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            reelsRecyclerView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
            }
            reelsRecyclerView.updatePadding(bottom = insets.bottom)

            windowInsets
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            initCursorLoader()
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?) = when (id) {
        MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal -> {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val selection = ("("
                    + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                    + " OR "
                    + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                    + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                    + ")")
            CursorLoader(
                requireContext(),
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            )
        }

        else -> throw Exception("Unknown ID $id")
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        thumbnailAdapter.changeCursor(null)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        thumbnailAdapter.changeCursor(data)
    }

    private fun initCursorLoader() {
        loaderManagerInstance.initLoader(
            MediaStoreRequests.MEDIA_STORE_REELS_LOADER_ID.ordinal, null, this
        )
    }

    companion object {
        fun createBundle() = CommonNavigationArguments().toBundle()

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance() = ReelsFragment().apply {
            arguments = createBundle()
        }
    }
}
