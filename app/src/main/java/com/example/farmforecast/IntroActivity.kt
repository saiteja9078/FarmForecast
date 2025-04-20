package com.example.farmforecast

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_act)

        videoView = findViewById(R.id.introVideoView)

        // Set the video path (put video in res/raw/)
        val videoUri = Uri.parse("android.resource://${packageName}/${R.raw.intro}")
        videoView.setVideoURI(videoUri)

        // When video finishes, go to MainActivity
        videoView.setOnCompletionListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        actionBar?.hide() // Just in case, but probably not needed
        supportActionBar?.hide() // This is the real one
        videoView.start()
    }

}
