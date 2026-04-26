package org.wolmics.soundboardplus

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatClientApi
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.util.ToastManager
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

object SoundboardAudioSystem {

    private var api: VoicechatApi? = null
    private var clientApi: VoicechatClientApi? = null
    private var localAudioChannel: ClientStaticAudioChannel? = null

    private val activeSounds = ConcurrentLinkedQueue<PlayingSound>()
    private const val FRAME_SIZE = 960

    var playerVolume: Float = SoundboardConfig.data.playerVolume
    var localVolume: Float = SoundboardConfig.data.localVolume

    var playbackPaused: Boolean = false

    fun initialize(api: VoicechatApi) {
        this.api = api
    }

    fun onClientConnection(event: ClientVoicechatConnectionEvent) {
        if (event.isConnected) {
            clientApi = event.voicechat
            val category = clientApi!!.volumeCategoryBuilder()
                .setId("soundboard")
                .setName("Soundboard")
                .build()
            clientApi!!.registerClientVolumeCategory(category)

            localAudioChannel = clientApi!!.createStaticAudioChannel(UUID.randomUUID())
            localAudioChannel?.category = category.id
        } else {
            stopAll()
            clientApi = null
            localAudioChannel = null
        }
    }

    // 0.0–1.0 progress for [name], or null if not playing
    fun getProgress(name: String): Double? =
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.progress

    // Total sample count for [name], or null if not playing
    fun getTotalSamples(name: String): Int? =
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.totalSamples

    // Seek [name] to [fraction] (0.0–1.0). No-op if not playing
    fun seekTo(name: String, fraction: Double) {
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.seekTo(fraction)
    }

    // Returns the name of the only active sound, or null if 0 or 2+ are playing
    fun getSinglePlayingName(): String? =
        activeSounds.filter { it.playbackState == PlaybackState.PLAYING }
            .takeIf { it.size == 1 }
            ?.first()?.name

    fun getSoundRepeat(name: String): Boolean? =
        activeSounds.find { it.name == name }?.repeat

    fun setSoundRepeat(name: String, repeat: Boolean) {
        activeSounds.find { it.name == name }?.repeat = repeat
    }

    fun onMergeSound(event: MergeClientSoundEvent) {
        clientApi ?: return

        if (!playbackActive()) return

        val mixedAudioPlayer = ShortArray(FRAME_SIZE)
        val mixedAudioLocal = ShortArray(FRAME_SIZE)
        val playLocally = SoundboardConfig.data.playLocally
        var hasAudio = false

        val iterator = activeSounds.iterator()
        while (iterator.hasNext()) {
            val sound = iterator.next()

            // Still decoding — skip this frame but keep it in the queue
            if (sound.playbackState == PlaybackState.LOADING) continue

            if (sound.isFinished) {
                if (!sound.repeat) {
                    iterator.remove()
                    continue
                }
                sound.repeatSound()
            }

            hasAudio = true

            val samplesToRead = min(FRAME_SIZE, sound.remaining)

            for (i in 0 until samplesToRead) {
                val rawSample = sound.readNext()

                mixSample(mixedAudioPlayer, i, rawSample, playerVolume)

                if (playLocally) {
                    mixSample(mixedAudioLocal, i, rawSample, localVolume)
                }
            }
        }

        if (hasAudio) {
            event.mergeAudio(mixedAudioPlayer)

            if (playLocally) {
                localAudioChannel?.play(mixedAudioLocal)
            }
        }
    }

    fun playbackActive(): Boolean {
        val api = clientApi ?: return false
        if (api.isDisabled || (api.isMuted && !SoundboardConfig.data.playWhileMuted)) {
            if (activeSounds.isNotEmpty()) activeSounds.clear()
            return false
        }

        if (activeSounds.isEmpty() || playbackPaused) {
            if (activeSounds.isEmpty() && playbackPaused) playbackPaused = false
            return false
        }
        return true
    }

    private fun mixSample(buffer: ShortArray, index: Int, sample: Short, volume: Float) {
        val weightedSample = (sample * volume).toInt()
        var result = buffer[index] + weightedSample

        if (result > Short.MAX_VALUE) result = Short.MAX_VALUE.toInt()
        else if (result < Short.MIN_VALUE) result = Short.MIN_VALUE.toInt()

        buffer[index] = result.toShort()
    }

    fun playFile(file: File) {
        val client = Minecraft.getInstance()
        val api = clientApi

        if (api == null) {
            ToastManager.createToast(Component.literal("§cVoice chat not connected!"), 1500)
            return
        }

        if (api.isMuted && !SoundboardConfig.data.playWhileMuted) {
            ToastManager.createToast(Component.literal("§cCannot play soundboard while muted!"), 1500)
            return
        }

        if (SoundboardConfig.data.playOnlyOne && activeSounds.isNotEmpty()) activeSounds.clear()

        // Add the sound immediately in LOADING state so the UI can reflect it
        val playingSound = PlayingSound(file.name)
        activeSounds.add(playingSound)

        CompletableFuture.runAsync {
            try {
                val pcmData = decodeMp3(file)
                if (pcmData != null && pcmData.isNotEmpty()) {
                    playingSound.setSamples(pcmData) // transitions to PLAYING internally
                } else {
                    activeSounds.remove(playingSound)
                    client.execute {
                        ToastManager.createToast(Component.literal("§cFailed to decode: ${file.name}"), 2500)
                    }
                }
            } catch (e: Exception) {
                activeSounds.remove(playingSound)
                e.printStackTrace()
            }
        }
    }

    fun isPlaying(file: String): Boolean {
        return activeSounds.any { it.name == file && it.playbackState != PlaybackState.STOPPED }
    }

    fun stop(file: String) {
        activeSounds.removeIf { it.name == file }
    }

    fun setVolume(localVol: Float, playerVol: Float) {
        localVolume = localVol
        playerVolume = playerVol
    }

    fun stopAll() {
        activeSounds.clear()
    }

    private fun decodeMp3(file: File): ShortArray? {
        val currentApi = api ?: return null

        return try {
            BufferedInputStream(Files.newInputStream(file.toPath())).use { stream ->
                val decoder = currentApi.createMp3Decoder(stream) ?: return null
                val rawPcm = decoder.decode()
                val format = decoder.audioFormat
                if (format.channels == 2) stereoToMono(rawPcm) else rawPcm
            }
        } catch (e: Exception) {
            System.err.println("Error decoding ${file.name}: ${e.message}")
            null
        }
    }

    private fun stereoToMono(stereo: ShortArray): ShortArray {
        val mono = ShortArray(stereo.size / 2)
        for (i in mono.indices) {
            val left = stereo[i * 2].toInt()
            val right = stereo[i * 2 + 1].toInt()
            mono[i] = ((left + right) / 2).toShort()
        }
        return mono
    }

    private class PlayingSound(val name: String) {

        private var samples: ShortArray = ShortArray(0)
        private var cursor = 0
        var repeat = false

        @Volatile
        var playbackState: PlaybackState = PlaybackState.LOADING
            private set

        // Called from the decode thread once samples are ready
        fun setSamples(data: ShortArray) {
            samples = data
            playbackState = PlaybackState.PLAYING
        }

        val totalSamples: Int get() = samples.size

        val progress: Double get() = if (samples.isEmpty()) 0.0 else cursor.toDouble() / samples.size

        val isFinished: Boolean
            get() = playbackState == PlaybackState.PLAYING && cursor >= samples.size

        val remaining: Int
            get() = samples.size - cursor

        fun seekTo(fraction: Double) {
            cursor = (fraction * samples.size).toInt().coerceIn(0, samples.size)
        }

        fun readNext(): Short {
            return if (cursor < samples.size) samples[cursor++] else 0
        }

        fun repeatSound() {
            cursor = 0
        }
    }
}

enum class PlaybackState {
    LOADING,
    PLAYING,
    STOPPED
}