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
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.getViewProperty
import org.lineageos.glimpse.thumbnail.ThumbnailPagingAdapter
import org.lineageos.glimpse.thumbnail.ThumbnailPagingLayoutManager
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.PagedMediaViewModel

/**
 * A fragment showing a list of media with thumbnails.
 * Use the [ReelsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ReelsFragment : Fragment(R.layout.fragment_reels) {
    // View models
    private val pagedMediaViewModel: PagedMediaViewModel by viewModels { PagedMediaViewModel.Factory }

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
                    requireContext(), R.string.app_permissions_toast, Toast.LENGTH_SHORT
                ).show()
                requireActivity().finish()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    pagedMediaViewModel.pagingFlow.collectLatest { pagingData ->
                        thumbnailAdapter.submitData(pagingData)
                    }
                }
                permissionsUtils.showManageMediaPermissionDialogIfNeeded()
            }
        }
    }

    // MediaStore
    private val thumbnailAdapter by lazy {
        ThumbnailPagingAdapter { media, position, anchor ->
            val extras = FragmentNavigatorExtras(
                anchor to "${media.id}"
            )
            parentNavController.navigate(
                R.id.action_mainFragment_to_mediaViewerFragment,
                MediaViewerFragment.createBundle(
                    null, media, position
                ),
                null,
                extras
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        reelsRecyclerView.layoutManager = ThumbnailPagingLayoutManager(
            requireContext(), thumbnailAdapter
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
            viewLifecycleOwner.lifecycleScope.launch {
                pagedMediaViewModel.pagingFlow.collectLatest { pagingData ->
                    thumbnailAdapter.submitData(pagingData)
                }
            }
            permissionsUtils.showManageMediaPermissionDialogIfNeeded()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        reelsRecyclerView.layoutManager = ThumbnailPagingLayoutManager(
            requireContext(), thumbnailAdapter
        )
    }


    companion object {
        private fun createBundle() = bundleOf()

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
