/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.glimpse.fragments

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.lineageos.glimpse.R
import org.lineageos.glimpse.ext.*
import org.lineageos.glimpse.models.Album
import org.lineageos.glimpse.models.Media
import org.lineageos.glimpse.models.MediaType
import org.lineageos.glimpse.thumbnail.MediaViewerAdapter
import org.lineageos.glimpse.utils.PermissionsUtils
import org.lineageos.glimpse.viewmodels.MediaViewModel
import java.text.SimpleDateFormat

/**
 * A fragment showing a media that supports scrolling before and after it.
 * Use the [MediaViewerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MediaViewerFragment : Fragment(R.layout.fragment_media_viewer) {
    // View models
    private val mediaViewModel: MediaViewModel by viewModels { MediaViewModel.Factory }

    // Views
    private val adjustButton by getViewProperty<ImageButton>(R.id.adjustButton)
    private val backButton by getViewProperty<ImageButton>(R.id.backButton)
    private val constraintLayout by getViewProperty<ConstraintLayout>(R.id.constraintLayout)
    private val bottomSheetLinearLayout by getViewProperty<LinearLayout>(R.id.bottomSheetLinearLayout)
    private val dateTextView by getViewProperty<TextView>(R.id.dateTextView)
    private val deleteButton by getViewProperty<ImageButton>(R.id.deleteButton)
    private val favoriteButton by getViewProperty<ImageButton>(R.id.favoriteButton)
    private val shareButton by getViewProperty<ImageButton>(R.id.shareButton)
    private val timeTextView by getViewProperty<TextView>(R.id.timeTextView)
    private val topSheetConstraintLayout by getViewProperty<ConstraintLayout>(R.id.topSheetConstraintLayout)
    private val viewPager by getViewProperty<ViewPager2>(R.id.viewPager)

    private var restoreLastTrashedMediaFromTrash: (() -> Unit)? = null

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
                    mediaViewModel.setBucketId(album?.id)
                    mediaViewModel.mediaForAlbum.collect(::initData)
                }
            }
        }
    }

    // Player
    private val exoPlayer by lazy {
        ExoPlayer.Builder(requireContext()).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    // Adapter
    private val startPostponedEnterTransitionUnit = { startPostponedEnterTransition() }
    private val mediaViewerAdapter by lazy {
        MediaViewerAdapter(
            exoPlayer,
            mediaViewModel.mediaPositionLiveData,
            startPostponedEnterTransitionUnit
        )
    }

    // Arguments
    private val album by lazy { arguments?.getParcelable(KEY_ALBUM, Album::class) }
    private val media by lazy { arguments?.getParcelable(KEY_MEDIA, Media::class) }

    // Contracts
    private val deleteUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.file_deletion_unsuccessful
                    } else {
                        R.plurals.file_deletion_successful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).show()
        }
    private val trashUriContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val succeeded = it.resultCode != Activity.RESULT_CANCELED

            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (succeeded) {
                        R.plurals.file_trashing_successful
                    } else {
                        R.plurals.file_trashing_unsuccessful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).also {
                restoreLastTrashedMediaFromTrash?.takeIf { succeeded }?.let { unit ->
                    it.setAction(R.string.file_trashing_undo) { unit() }
                }
            }.show()

            restoreLastTrashedMediaFromTrash = null
        }
    private val restoreUriFromTrashContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Snackbar.make(
                requireView(),
                resources.getQuantityString(
                    if (it.resultCode == Activity.RESULT_CANCELED) {
                        R.plurals.file_restoring_from_trash_unsuccessful
                    } else {
                        R.plurals.file_restoring_from_trash_successful
                    },
                    1, 1
                ),
                Snackbar.LENGTH_LONG,
            ).setAnchorView(bottomSheetLinearLayout).show()
        }
    private val favoriteContract =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                favoriteButton.isSelected = it.isFavorite
            }
        }

    private val onPageChangeCallback = object : OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            super.onPageSelected(position)

            if (mediaViewerAdapter.itemCount <= 0) {
                // No medias, bail out
                // TODO: Do better once we support showing a specific album
                //       from intents (dialog and such)
                findNavController().popBackStack()
                return
            }

            this@MediaViewerFragment.mediaViewModel.mediaPosition = position

            val media = mediaViewerAdapter.getItemAtPosition(position)

            dateTextView.text = dateFormatter.format(media.dateAdded)
            timeTextView.text = timeFormatter.format(media.dateAdded)
            favoriteButton.isSelected = media.isFavorite
            deleteButton.setImageResource(
                when (media.isTrashed) {
                    true -> R.drawable.ic_restore_from_trash
                    false -> R.drawable.ic_delete
                }
            )

            if (media.mediaType == MediaType.VIDEO) {
                exoPlayer.setMediaItem(MediaItem.fromUri(media.externalContentUri))
                exoPlayer.seekTo(C.TIME_UNSET)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            } else {
                exoPlayer.stop()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition =
            TransitionInflater.from(requireContext()).inflateTransition(R.transition.shared_image)
                .apply {
                    addListener(object : Transition.TransitionListener {
                        override fun onTransitionStart(transition: Transition) {
                            arrayOf(
                                constraintLayout,
                                bottomSheetLinearLayout,
                                topSheetConstraintLayout
                            ).forEach {
                                it.alpha = 0f
                                it.animate()
                                    .alpha(1f)
                            }
                        }

                        override fun onTransitionEnd(transition: Transition) {}
                        override fun onTransitionCancel(transition: Transition) {}
                        override fun onTransitionPause(transition: Transition) {}
                        override fun onTransitionResume(transition: Transition) {}
                    })
                }
    }

    override fun onResume() {
        super.onResume()

        exoPlayer.play()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        postponeEnterTransition()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mediaViewModel.mediaPosition = arguments?.getInt(KEY_POSITION, -1)!!

        backButton.setOnClickListener {
            findNavController().popBackStack()
        }

        deleteButton.setOnClickListener {
            trashMedia(mediaViewerAdapter.getItemAtPosition(viewPager.currentItem))
        }

        deleteButton.setOnLongClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.file_deletion_confirm_title)
                    .setMessage(
                        resources.getQuantityString(
                            R.plurals.file_deletion_confirm_message, 1, 1
                        )
                    ).setPositiveButton(android.R.string.ok) { _, _ ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            deleteUriContract.launch(
                                requireContext().contentResolver.createDeleteRequest(
                                    it.externalContentUri
                                )
                            )
                        } else {
                            it.delete(requireContext().contentResolver)
                        }
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        // Do nothing
                    }
                    .show()

                true
            }

            false
        }

        favoriteButton.setOnClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    favoriteContract.launch(
                        requireContext().contentResolver.createFavoriteRequest(
                            !it.isFavorite, it.externalContentUri
                        )
                    )
                } else {
                    it.favorite(requireContext().contentResolver, !it.isFavorite)
                    favoriteButton.isSelected = !it.isFavorite
                }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            topSheetConstraintLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                rightMargin = insets.right
                topMargin = insets.top
            }
            bottomSheetLinearLayout.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = insets.bottom
                leftMargin = insets.left
                rightMargin = insets.right
            }

            windowInsets
        }

        viewPager.adapter = mediaViewerAdapter
        viewPager.offscreenPageLimit = 2
        viewPager.registerOnPageChangeCallback(onPageChangeCallback)

        shareButton.setOnClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                val intent = Intent().shareIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        adjustButton.setOnClickListener {
            mediaViewerAdapter.getItemAtPosition(viewPager.currentItem).let {
                val intent = Intent().editIntent(it)
                startActivity(Intent.createChooser(intent, null))
            }
        }

        if (!permissionsUtils.mainPermissionsGranted()) {
            mainPermissionsRequestLauncher.launch(PermissionsUtils.mainPermissions)
        } else {
            media?.let {
                mediaViewerAdapter.data = arrayOf(it)
            }
            viewLifecycleOwner.lifecycleScope.launch {
                mediaViewModel.setBucketId(album?.id)
                mediaViewModel.mediaForAlbum.collect(::initData)
            }
        }
    }

    override fun onDestroyView() {
        viewPager.unregisterOnPageChangeCallback(onPageChangeCallback)

        exoPlayer.stop()

        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()

        exoPlayer.pause()
    }

    override fun onDestroy() {
        exoPlayer.release()

        super.onDestroy()
    }

    private fun trashMedia(media: Media, trash: Boolean = !media.isTrashed) {
        if (trash) {
            restoreLastTrashedMediaFromTrash = { trashMedia(media, false) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contract = when (trash) {
                true -> trashUriContract
                false -> restoreUriFromTrashContract
            }
            contract.launch(
                requireContext().contentResolver.createTrashRequest(
                    trash, media.externalContentUri
                )
            )
        } else {
            media.trash(requireContext().contentResolver, trash)
        }
    }

    private fun initData(data: List<Media>) {
        mediaViewerAdapter.data = data.toTypedArray()
        viewPager.setCurrentItem(mediaViewModel.mediaPosition, false)
        onPageChangeCallback.onPageSelected(mediaViewModel.mediaPosition)
    }

    companion object {
        private const val KEY_ALBUM = "album"
        private const val KEY_MEDIA = "media"
        private const val KEY_POSITION = "position"

        private val dateFormatter = SimpleDateFormat.getDateInstance()
        private val timeFormatter = SimpleDateFormat.getTimeInstance()

        fun createBundle(
            album: Album?,
            media: Media,
            position: Int,
        ) = bundleOf(
            KEY_ALBUM to album,
            KEY_MEDIA to media,
            KEY_POSITION to position,
        )

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param album Album.
         * @return A new instance of fragment ReelsFragment.
         */
        fun newInstance(
            album: Album?,
            media: Media,
            position: Int,
        ) = MediaViewerFragment().apply {
            arguments = createBundle(
                album,
                media,
                position,
            )
        }
    }
}
