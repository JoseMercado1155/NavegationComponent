package com.example.navigationcomponentexample.Grabadora

import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.example.navigationcomponentexample.R
import java.io.IOException

class AudioPlayerActivity : AppCompatActivity() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var btnPlay: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var handler: Handler
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audio_player)

        val filepath = intent.getStringExtra("filepath") ?: ""
        if (filepath.isNotEmpty()) {
            mediaPlayer = MediaPlayer()
            try {
                mediaPlayer.setDataSource(filepath)
                mediaPlayer.prepare()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        btnPlay = findViewById(R.id.btnPlay)
        seekBar = findViewById(R.id.seekBar)

        handler = Handler(Looper.getMainLooper())

        // Configura la duración máxima de la SeekBar
        seekBar.max = mediaPlayer.duration

        btnPlay.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                pauseAudio()
            } else {
                playAudio()
            }
        }

        // Actualizar el progreso de la SeekBar con un Runnable
        runnable = Runnable {
            if (mediaPlayer.isPlaying) {
                seekBar.progress = mediaPlayer.currentPosition
                handler.postDelayed(runnable, 1000) // Actualiza cada segundo
            }
        }

        // Permitir que el usuario desplace la SeekBar manualmente
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer.seekTo(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Opcional: Pausar la actualización mientras el usuario ajusta manualmente
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Opcional: Reanudar la actualización si pausaste al inicio
            }
        })
    }

    private fun playAudio() {
        mediaPlayer.start()
        btnPlay.background = ResourcesCompat.getDrawable(resources, R.drawable.ic_pause_circle, theme)
        handler.post(runnable) // Inicia el Runnable para actualizar la SeekBar
    }

    private fun pauseAudio() {
        mediaPlayer.pause()
        btnPlay.background = ResourcesCompat.getDrawable(resources, R.drawable.ic_play_circle, theme)
        handler.removeCallbacks(runnable) // Detiene la actualización de la SeekBar
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.release() // Libera recursos del MediaPlayer
        handler.removeCallbacks(runnable) // Asegura que se detenga la actualización
    }
}



