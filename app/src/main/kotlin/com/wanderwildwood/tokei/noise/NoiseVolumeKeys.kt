package com.wanderwildwood.tokei.noise

import android.app.Activity
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * While the sleep noise plays, the phone's volume keys turn the noise up and down on this
 * screen: the noise plays at the media volume, and without this a Kompakt's keys moved the
 * ringtone instead. When the noise stops the keys go back to what the phone gives them.
 */
@Composable
fun NoiseVolumeKeys(activity: Activity) {
    val noise by NoiseService.current.collectAsState()
    LaunchedEffect(noise.playing) {
        activity.volumeControlStream =
            if (noise.playing) AudioManager.STREAM_MUSIC else AudioManager.USE_DEFAULT_STREAM_TYPE
    }
}
