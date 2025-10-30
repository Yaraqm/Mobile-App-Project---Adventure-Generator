package com.example.mobileproject

import android.app.Activity
import android.app.Application
import android.media.MediaPlayer
import android.os.Bundle
import com.example.mobileproject.utils.SupabaseHelper
import com.example.mobileproject.BuildConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

class MyApplication : Application(), Application.ActivityLifecycleCallbacks {

    var mediaPlayer: MediaPlayer? = null
    var isMusicOn: Boolean = true
        private set

    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)

        // ✅ Initialize Firebase App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        // ✅ Initialize Supabase connection (logs confirmation)
        SupabaseHelper.logConnection()

        // Prepare MediaPlayer
        mediaPlayer = MediaPlayer.create(this, R.raw.backgroundmusic)
        mediaPlayer?.isLooping = true
    }

    fun toggleMusic() {
        if (isMusicOn) {
            mediaPlayer?.pause()
        } else {
            mediaPlayer?.start()
        }
        isMusicOn = !isMusicOn
    }

    fun startMusicIfOn() {
        if (isMusicOn && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (startedActivities == 0) {
            startMusicIfOn() // If app comes to foreground, keep music on loop unless muted
        }
        startedActivities++
    }

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {
        startedActivities--
        if (startedActivities == 0) {
            pauseMusic() // If app goes to background, pause the music service
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {}
}
