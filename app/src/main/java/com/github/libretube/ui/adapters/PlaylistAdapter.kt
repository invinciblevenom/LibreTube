package com.github.libretube.ui.adapters

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.github.libretube.R
import com.github.libretube.api.PlaylistsHelper
import com.github.libretube.api.obj.StreamItem
import com.github.libretube.databinding.VideoRowBinding
import com.github.libretube.enums.PlaylistType
import com.github.libretube.extensions.TAG
import com.github.libretube.extensions.dpToPx
import com.github.libretube.extensions.toID
import com.github.libretube.extensions.toastFromMainDispatcher
import com.github.libretube.helpers.ImageHelper
import com.github.libretube.helpers.NavigationHelper
import com.github.libretube.ui.base.BaseActivity
import com.github.libretube.ui.extensions.setFormattedDuration
import com.github.libretube.ui.extensions.setWatchProgressLength
import com.github.libretube.ui.sheets.VideoOptionsBottomSheet
import com.github.libretube.ui.viewholders.PlaylistViewHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * @param originalFeed original, unsorted feed, needed in order to delete the proper video from
 * playlists
 */
class PlaylistAdapter(
    private val originalFeed: MutableList<StreamItem>,
    private val sortedFeed: MutableList<StreamItem>,
    private val playlistId: String,
    private val playlistType: PlaylistType
) : RecyclerView.Adapter<PlaylistViewHolder>() {

    private var visibleCount = minOf(20, sortedFeed.size)

    override fun getItemCount(): Int {
        return when (playlistType) {
            PlaylistType.PUBLIC -> sortedFeed.size
            else -> minOf(visibleCount, sortedFeed.size)
        }
    }

    fun updateItems(newItems: List<StreamItem>) {
        val oldSize = sortedFeed.size
        sortedFeed.addAll(newItems)
        notifyItemRangeInserted(oldSize, sortedFeed.size)
    }

    fun showMoreItems() {
        val oldSize = visibleCount
        visibleCount += minOf(10, sortedFeed.size - oldSize)
        if (visibleCount == oldSize) return
        notifyItemRangeInserted(oldSize, visibleCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PlaylistViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = VideoRowBinding.inflate(layoutInflater, parent, false)
        return PlaylistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PlaylistViewHolder, position: Int) {
        val streamItem = sortedFeed[position]
        holder.binding.apply {
            videoTitle.text = streamItem.title
            videoInfo.text = streamItem.uploaderName
            channelImage.isGone = true

            thumbnailDuration.setFormattedDuration(streamItem.duration!!, streamItem.isShort)
            ImageHelper.loadImage(streamItem.thumbnail, thumbnail)
            root.setOnClickListener {
                NavigationHelper.navigateVideo(root.context, streamItem.url, playlistId)
            }
            val videoId = streamItem.url!!.toID()
            val videoName = streamItem.title!!
            root.setOnLongClickListener {
                VideoOptionsBottomSheet(videoId, videoName) {
                    notifyItemChanged(position)
                }
                    .show(
                        (root.context as BaseActivity).supportFragmentManager,
                        VideoOptionsBottomSheet::class.java.name
                    )
                true
            }

            if (!streamItem.uploaderUrl.isNullOrBlank()) {
                videoInfo.setOnClickListener {
                    NavigationHelper.navigateChannel(root.context, streamItem.uploaderUrl.toID())
                }
                // add some extra padding to make it easier to click
                val extraPadding = (3).dpToPx().toInt()
                videoInfo.updatePadding(top = extraPadding, bottom = extraPadding)
            }

            watchProgress.setWatchProgressLength(videoId, streamItem.duration)
        }
    }

    fun removeFromPlaylist(context: Context, position: Int) {
        // get the index of the video in the playlist
        // could vary due to playlist sorting by the user
        val playlistIndex = originalFeed.indexOfFirst {
            it.url == sortedFeed[position].url
        }.takeIf { it >= 0 } ?: return

        originalFeed.removeAt(playlistIndex)
        sortedFeed.removeAt(position)
        visibleCount -= 1
        (context as Activity).runOnUiThread {
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, itemCount)
        }
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                PlaylistsHelper.removeFromPlaylist(playlistId, playlistIndex)
            } catch (e: Exception) {
                Log.e(TAG(), e.toString())
                appContext.toastFromMainDispatcher(R.string.unknown_error)
            }
        }
    }
}
