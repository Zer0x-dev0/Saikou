package ani.saikou.media.anime.mpv

import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ani.saikou.media.anime.mpv.ui.PlayerScreen
import kotlinx.coroutines.launch


class PlayerActivity : AppCompatActivity() {
    private val playerModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
//val videoUrl ="https://a4.mp4upload.com:183/d/xkxu4m5az3b4quuoz6ru6isjkony2zxbfjihfj6xyv7j2l7rkflpcbk647c46yrqyoz456wl/video.mp4"
//        val videoUrl = "https://vault-15.owocdn.top/stream/15/01/bb57e882873b74aa9196aafe098b645acf0ad6db5c368da4f3bbf474b04d0b2f/uwu.m3u8"
        val headers = mapOf("Referer" to "https://www.mp4upload.com/")
        val videoUrl :String = "https://seiryuu.vid-cdn.xyz/5170df05-ee81-4e84-8dea-617fede13ba1/master.m3u8"
        setContent {
            PlayerScreen(
                viewModel = playerModel,
                videoUrl = videoUrl,
//                headers = headers
            )
        }
        hideSystemUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        playerModel.releasePlayer()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
        }
    }
}



