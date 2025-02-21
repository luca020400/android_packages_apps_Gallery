/*
 * SPDX-FileCopyrightText: 2023 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.gallery.thumbnail

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.NavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.lineageos.gallery.R
import org.lineageos.gallery.fragments.AlbumFragment
import org.lineageos.gallery.models.Album

class AlbumThumbnailAdapter(
    private val navController: NavController,
) : RecyclerView.Adapter<AlbumThumbnailAdapter.AlbumViewHolder>() {
    private var albums: Array<Album>? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val view = layoutInflater.inflate(R.layout.album_thumbnail_view, parent, false)

        return AlbumViewHolder(view, navController)
    }

    override fun getItemCount() = albums?.size ?: 0

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        albums?.let {
            holder.bind(it[position])
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun changeArray(array: Array<Album>?) {
        if (albums.contentEquals(array)) {
            return
        }

        albums = array

        array?.let {
            notifyDataSetChanged()
        }
    }

    class AlbumViewHolder(
        itemView: View,
        private val navController: NavController,
    ) : RecyclerView.ViewHolder(itemView) {
        // Views
        private val descriptionTextView = itemView.findViewById<TextView>(R.id.descriptionTextView)!!
        private val itemsCountTextView = itemView.findViewById<TextView>(R.id.itemsCountTextView)!!
        private val thumbnailImageView = itemView.findViewById<ImageView>(R.id.thumbnailImageView)!!

        fun bind(album: Album) {
            descriptionTextView.text = album.name
            itemsCountTextView.text = itemView.resources.getQuantityString(
                R.plurals.album_thumbnail_items, album.size, album.size
            )

            Glide.with(itemView)
                .load(album.thumbnail)
                .placeholder(R.drawable.thumbnail_placeholder)
                .into(thumbnailImageView)

            itemView.setOnClickListener {
                navController.navigate(
                    R.id.action_mainFragment_to_albumFragment,
                    AlbumFragment.createBundle(album)
                )
            }
        }
    }
}
