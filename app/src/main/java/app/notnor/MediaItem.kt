package app.notnor

import android.net.Uri

data class MediaItem(val uri: Uri, val name: String, val seasonEpisode: String? = null)