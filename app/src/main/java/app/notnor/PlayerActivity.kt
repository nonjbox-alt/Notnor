package app.notnor

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private val prefs by lazy { getSharedPreferences("notnor", MODE_PRIVATE) }
    private var key = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK
        key = intent.getStringExtra("key") ?: intent.getStringExtra("uri") ?: ""
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = PlayerView(this).apply { useController = true; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; setShowSubtitleButton(true); setShowNextButton(false); setShowPreviousButton(false) }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        val title = TextView(this).apply { text = intent.getStringExtra("name") ?: "notnor"; setTextColor(Color.WHITE); textSize = 14f; setPadding(20, 18, 20, 18); setBackgroundColor(0x66000000) }
        root.addView(title, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP))
        setContentView(root)
        val exo = ExoPlayer.Builder(this).build(); player = exo; view.player = exo
        exo.setMediaItem(ExoMediaItem.fromUri(Uri.parse(intent.getStringExtra("uri"))))
        exo.prepare(); exo.seekTo(prefs.getLong("pos_$key", 0L)); exo.playWhenReady = true
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) prefs.edit { putBoolean("done_$key", true); putLong("pos_$key", 0L) }
            }
        })
    }
    override fun onPause() { super.onPause(); player?.let { prefs.edit { putLong("pos_$key", it.currentPosition); putBoolean("done_$key", it.playbackState == Player.STATE_ENDED) }; it.pause() } }
    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}