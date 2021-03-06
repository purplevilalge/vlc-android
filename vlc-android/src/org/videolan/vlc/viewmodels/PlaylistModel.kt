/*****************************************************************************
 * PlaylistModel.kt
 *****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.viewmodels

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.fragment.app.Fragment
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.media.MediaWrapper
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.util.EmptyPBSCallback
import org.videolan.vlc.util.LiveDataset
import org.videolan.vlc.util.PlaylistFilterDelegate

class PlaylistModel(private val service: PlaybackService) : ScopedModel(), PlaybackService.Callback by EmptyPBSCallback {

    val dataset = LiveDataset<MediaWrapper>()
    var originalDataset : MutableList<MediaWrapper>? = null
    val selection : Int
        get() = if (filtering) -1 else service.playlistManager.currentIndex
    var filtering = false
    val progress by lazy(LazyThreadSafetyMode.NONE) {
        MediatorLiveData<PlaybackProgress>().apply {
            addSource(service.playlistManager.player.progress) {
                value = PlaybackProgress(it?.time ?: 0L, it?.length ?: 0L)
            }
        }
    }

    private val filter by lazy(LazyThreadSafetyMode.NONE) { PlaylistFilterDelegate(dataset) }

    fun setup() {
        service.addCallback(this)
        update()
    }

    override fun update() {
        dataset.value = service.medias.toMutableList()
    }

    fun insertMedia(position: Int, media: MediaWrapper) = service.insertItem(position, media)

    fun remove(position: Int) = service.remove(position)

    fun move(from: Int, to: Int) = service.moveItem(from, to)

    fun filter(query: CharSequence?) {
        val filtering = query != null
        if (this.filtering != filtering) {
            this.filtering = filtering
            originalDataset = if (filtering) dataset.value.toMutableList() else null
        }
        launch { filter.filter(query) }
    }

    public override fun onCleared() {
        service.removeCallback(this)
        super.onCleared()
    }

    fun getPlaylistPosition(position: Int, media: MediaWrapper): Int {
        val list = originalDataset ?: dataset.value
        if (list[position] == media) return position
        else {
            for ((index, item) in list.withIndex()) if (item == media) {
                return index
            }
        }
        return -1
    }

    companion object {
        fun get(fragment: androidx.fragment.app.Fragment, service: PlaybackService) = ViewModelProviders.of(fragment, PlaylistModel.Factory(service)).get(PlaylistModel::class.java)
    }

    class Factory(val service: PlaybackService): ViewModelProvider.NewInstanceFactory() {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PlaylistModel(service) as T
        }
    }
}

data class PlaybackProgress(
        val time: Long,
        val length: Long,
        val timeText : String = Tools.millisToString(time),
        val lengthText : String  = Tools.millisToString(length))