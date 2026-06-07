package org.wolmics.soundboardplus

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatClientApi
import de.maxhenkel.voicechat.api.audiochannel.ClientStaticAudioChannel
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent
import javazoom.jl.decoder.*
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.util.ToastManager
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

object SoundboardAudioSystem {

    private var api: VoicechatApi? = null
    private var clientApi: VoicechatClientApi? = null
    private var localAudioChannel: ClientStaticAudioChannel? = null

    private val activeSounds = ConcurrentLinkedQueue<PlayingSound>()
    private const val FRAME_SIZE = 960
    private const val SAMPLE_RATE = 48000
    private const val STREAMING_THRESHOLD_SECONDS = 5 * 60
    private const val STREAM_BUFFER_SAMPLES = SAMPLE_RATE * 10

    var playerVolume: Float = SoundboardConfig.data.playerVolume
    var localVolume: Float = SoundboardConfig.data.localVolume
    var playbackPaused: Boolean = false

    // Stores the voicechat API reference on mod init.
    fun initialize(api: VoicechatApi) {
        this.api = api
    }

    // Called when the player connects to or disconnects from a voice chat server.
    // On connect: registers a volume category and creates the local playback channel.
    // On disconnect: stops all sounds and clears references.
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

    // Returns playback progress (0.0–1.0) for the named sound, or null if not playing.
    fun getProgress(name: String): Double? =
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.progress

    // Returns the total sample count for the named sound, or null if not playing.
    fun getTotalSamples(name: String): Int? =
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.totalSamples

    // Seeks the named sound to the given fraction (0.0 = start, 1.0 = end).
    fun seekTo(name: String, fraction: Double) {
        activeSounds.find { it.name == name && it.playbackState == PlaybackState.PLAYING }?.seekTo(fraction)
    }

    // Returns the name of the currently playing sound if exactly one is playing, else null.
    fun getSinglePlayingName(): String? =
        activeSounds.filter { it.playbackState == PlaybackState.PLAYING }
            .takeIf { it.size == 1 }?.first()?.name

    // Returns whether the named sound is set to repeat, or null if it isn't active.
    fun getSoundRepeat(name: String): Boolean? =
        activeSounds.find { it.name == name }?.repeat

    // Sets the repeat flag on the named sound.
    fun setSoundRepeat(name: String, repeat: Boolean) {
        activeSounds.find { it.name == name }?.repeat = repeat
    }

    // Called every audio frame by the voice chat engine. Mixes all active sounds
    // into a single frame that gets sent to other players, and optionally plays
    // the same audio locally through the static channel.
    fun onMergeSound(event: MergeClientSoundEvent) {
        clientApi ?: return
        if (!playbackActive()) return

        val mixedPlayer = ShortArray(FRAME_SIZE)
        val mixedLocal  = ShortArray(FRAME_SIZE)
        val playLocally = SoundboardConfig.data.playLocally
        var hasAudio = false

        val iterator = activeSounds.iterator()
        while (iterator.hasNext()) {
            val sound = iterator.next()
            if (sound.playbackState == PlaybackState.LOADING) continue
            if (sound.isFinished) {
                if (!sound.repeat) { iterator.remove(); continue }
                sound.repeatSound()
            }
            hasAudio = true
            val count = min(FRAME_SIZE, sound.remaining)
            for (i in 0 until count) {
                val s = sound.readNext()
                mixSample(mixedPlayer, i, s, playerVolume)
                if (playLocally) mixSample(mixedLocal, i, s, localVolume)
            }
        }

        if (hasAudio) {
            event.mergeAudio(mixedPlayer)
            if (playLocally) localAudioChannel?.play(mixedLocal)
        }
    }

    // Returns true if the audio engine should produce output this frame.
    // Clears sounds and resets pause state when conditions are not met.
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

    // Adds a scaled sample into the output buffer with clamping to prevent overflow.
    private fun mixSample(buf: ShortArray, i: Int, sample: Short, volume: Float) {
        val v = buf[i] + (sample * volume).toInt()
        buf[i] = v.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    // Starts playing a file. Short files (<5 min) are fully decoded into memory;
    // longer files are streamed from disk via a ring buffer on a background thread.
    fun playFile(file: File) {
        val api = clientApi
        if (api == null) {
            ToastManager.createToast(Text.literal("§cVoice chat not connected!"), 1500)
            return
        }
        if (api.isMuted && !SoundboardConfig.data.playWhileMuted) {
            ToastManager.createToast(Text.literal("§cCannot play soundboard while muted!"), 1500)
            return
        }
        if (SoundboardConfig.data.playOnlyOne && activeSounds.isNotEmpty()) activeSounds.clear()

        val sound = PlayingSound(file.name)
        activeSounds.add(sound)

        CompletableFuture.runAsync {
            try {
                if (shouldStream(file)) {
                    // streamLoop runs directly here — no second thread needed.
                    sound.streamLoop(file)
                } else {
                    val pcm = decodeWithVoicechatApi(file)
                    if (pcm != null && pcm.isNotEmpty()) {
                        sound.setSamples(pcm)
                    } else {
                        activeSounds.remove(sound)
                        MinecraftClient.getInstance().execute {
                            ToastManager.createToast(Text.literal("§cFailed to decode: ${file.name}"), 2500)
                        }
                    }
                }
            } catch (e: Exception) {
                activeSounds.remove(sound)
                e.printStackTrace()
            }
        }
    }

    // Reads the first frame header to estimate duration and decide whether to stream.
    private fun shouldStream(file: File): Boolean {
        return try {
            BufferedInputStream(Files.newInputStream(file.toPath())).use { bis ->
                val bs = Bitstream(bis)
                val header = bs.readFrame() ?: return true
                val bitrate = header.bitrate()
                bs.closeFrame()
                if (bitrate <= 0) return true
                (file.length() * 8.0 / bitrate).toInt() > STREAMING_THRESHOLD_SECONDS
            }
        } catch (_: Exception) { true }
    }

    // Decodes a short MP3 file entirely into a ShortArray using the voicechat API decoder.
    // Handles stereo-to-mono conversion and resampling to 48 kHz.
    private fun decodeWithVoicechatApi(file: File): ShortArray? {
        val currentApi = this.api ?: return null
        return try {
            BufferedInputStream(Files.newInputStream(file.toPath())).use { stream ->
                val decoder = currentApi.createMp3Decoder(stream) ?: return null
                var raw = decoder.decode()
                val fmt = decoder.audioFormat
                if (fmt.channels == 2) raw = stereoToMono(raw)
                val rate = fmt.sampleRate.toInt()
                if (rate != SAMPLE_RATE) resample(raw, rate) else raw
            }
        } catch (e: Exception) {
            System.err.println("Error decoding ${file.name}: ${e.message}")
            null
        }
    }

    // Averages left and right channels into a single mono channel.
    private fun stereoToMono(stereo: ShortArray): ShortArray {
        val mono = ShortArray(stereo.size / 2)
        for (i in mono.indices) {
            mono[i] = ((stereo[i * 2].toInt() + stereo[i * 2 + 1].toInt()) / 2).toShort()
        }
        return mono
    }

    // Linear interpolation resample from any sample rate to 48 kHz.
    private fun resample(input: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == SAMPLE_RATE) return input
        val out = ShortArray((input.size.toLong() * SAMPLE_RATE / fromRate).toInt())
        for (i in out.indices) {
            val pos = i.toDouble() * fromRate / SAMPLE_RATE
            val idx = pos.toInt()
            val frac = pos - idx
            val s0 = input.getOrElse(idx) { 0 }.toInt()
            val s1 = input.getOrElse(idx + 1) { 0 }.toInt()
            out[i] = (s0 + frac * (s1 - s0)).toInt().toShort()
        }
        return out
    }

    fun isPlaying(file: String): Boolean =
        activeSounds.any { it.name == file && it.playbackState != PlaybackState.STOPPED }

    fun stop(file: String) {
        activeSounds.filter { it.name == file }.forEach { it.stop() }
        activeSounds.removeIf { it.name == file }
    }

    fun setVolume(localVol: Float, playerVol: Float) {
        localVolume = localVol
        playerVolume = playerVol
    }

    fun stopAll() {
        activeSounds.forEach { it.stop() }
        activeSounds.clear()
    }

    // =========================================================================
    // PlayingSound — represents one active sound.
    //
    // Short files: the full PCM array is stored in `samples`, read by a cursor.
    // Long files:  a background decode thread fills a ring buffer; the mixer
    //              drains it one sample at a time via readNext().
    // =========================================================================
    private class PlayingSound(val name: String) {

        // Short-file state
        private var samples: ShortArray = ShortArray(0)
        private var cursor = 0

        // Ring buffer with power-of-two capacity for fast index masking.
        private val ringCapacity = nextPow2(STREAM_BUFFER_SAMPLES + FRAME_SIZE * 2)
        private val ring = ShortArray(ringCapacity)
        private val ringMask = ringCapacity - 1

        // Logical heads in range 0..2*ringCapacity to distinguish full vs empty.
        // writeHead: decode thread only. readHead: audio thread only.
        @Volatile private var writeHead = 0
        @Volatile private var readHead  = 0

        @Volatile private var decodeFinished = false
        @Volatile private var estimatedTotalSamples = 0
        private var samplesConsumed = 0  // audio thread only — no lock needed

        private val streamLock = ReentrantLock()
        private val bufferHasSpace = streamLock.newCondition()

        // Written by the audio thread to request a seek; read and cleared by the decode thread.
        @Volatile private var pendingSeekSample = -1L

        private var sourceSampleRate = 44100
        private var sourceChannels   = 2

        private var isStreaming = false

        var repeat = false

        @Volatile var playbackState: PlaybackState = PlaybackState.LOADING
            private set

        // Stores fully decoded PCM for short files and transitions to PLAYING.
        fun setSamples(data: ShortArray) {
            samples = data
            playbackState = PlaybackState.PLAYING
        }

        // Outer decode loop — runs streamDecode repeatedly to handle seeks and repeats.
        // Called directly on the CompletableFuture thread from playFile().
        fun streamLoop(file: File) {
            isStreaming = true
            var startAt = 0L
            while (!Thread.currentThread().isInterrupted) {
                streamDecode(file, startAt)

                if (playbackState == PlaybackState.STOPPED) break

                // Wait for a seek/repeat request or a stop signal.
                streamLock.withLock {
                    while (pendingSeekSample < 0L && playbackState != PlaybackState.STOPPED) {
                        bufferHasSpace.await()
                    }
                }

                if (playbackState == PlaybackState.STOPPED) break

                startAt = pendingSeekSample.coerceAtLeast(0L)
                pendingSeekSample = -1L
            }
        }

        // Decodes the file from startAtSample (in 48 kHz output samples) to EOF,
        // or returns early if a seek is posted or playback is stopped.
        private fun streamDecode(file: File, startAtSample: Long) {
            try {
                val bis       = BufferedInputStream(Files.newInputStream(file.toPath()), 65536)
                val bitstream = Bitstream(bis)
                val decoder   = Decoder()

                val firstHeader = bitstream.readFrame() ?: run { decodeFinished = true; return }

                sourceSampleRate = firstHeader.frequency()
                sourceChannels   = if (firstHeader.mode() == Header.SINGLE_CHANNEL) 1 else 2
                val resampleRatio = sourceSampleRate.toDouble() / SAMPLE_RATE

                // total_ms() accounts for ID3 tag overhead and gives an accurate duration.
                val durationMs = firstHeader.total_ms(file.length().toInt())
                if (durationMs > 0) estimatedTotalSamples = (durationMs / 1000.0 * SAMPLE_RATE).toInt()

                val samplesPerFrame = when (firstHeader.layer()) { 1 -> 384L; else -> 1152L }
                val skipSourceSamples = (startAtSample * resampleRatio).toLong()

                // Fast-skip whole frames without decoding them.
                var skipped = 0L
                var currentHeader: Header? = firstHeader
                while (currentHeader != null && skipped + samplesPerFrame <= skipSourceSamples) {
                    bitstream.closeFrame()
                    skipped += samplesPerFrame
                    currentHeader = bitstream.readFrame()
                }

                // Decode the landing frame and trim the samples before the seek point.
                if (currentHeader != null) {
                    val output = decoder.decodeFrame(currentHeader, bitstream) as SampleBuffer
                    val trim = (skipSourceSamples - skipped).toInt().coerceAtLeast(0)
                    pushPcmOutput(output, sourceChannels, resampleRatio, trimStart = trim)
                    bitstream.closeFrame()
                }

                if (playbackState == PlaybackState.LOADING && ringReadable() >= FRAME_SIZE) {
                    playbackState = PlaybackState.PLAYING
                }

                // Main decode loop — exits on EOF, seek request, or stop.
                while (!Thread.currentThread().isInterrupted) {
                    if (playbackState == PlaybackState.STOPPED) return
                    if (pendingSeekSample >= 0L) return

                    if (ringWritable() < SAMPLE_RATE) {
                        streamLock.withLock {
                            while (ringWritable() < SAMPLE_RATE &&
                                   playbackState != PlaybackState.STOPPED &&
                                   pendingSeekSample < 0L
                            ) { bufferHasSpace.await() }
                        }
                        continue
                    }

                    val header = bitstream.readFrame() ?: break
                    val output = decoder.decodeFrame(header, bitstream) as SampleBuffer
                    pushPcmOutput(output, sourceChannels, resampleRatio)
                    bitstream.closeFrame()

                    if (playbackState == PlaybackState.LOADING && ringReadable() >= FRAME_SIZE) {
                        playbackState = PlaybackState.PLAYING
                    }
                }

                decodeFinished = true
                if (playbackState == PlaybackState.LOADING) playbackState = PlaybackState.PLAYING

            } catch (e: BitstreamException) {
                decodeFinished = true
                if (playbackState == PlaybackState.LOADING) playbackState = PlaybackState.PLAYING
            } catch (e: Exception) {
                e.printStackTrace()
                decodeFinished = true
                playbackState = PlaybackState.STOPPED
            }
        }

        // Converts a decoded JLayer SampleBuffer to mono 48 kHz and writes it to the ring.
        // trimStart skips that many source samples from the front (used for seek landing frames).
        private fun pushPcmOutput(
            output: SampleBuffer,
            channels: Int,
            resampleRatio: Double,
            trimStart: Int = 0
        ) {
            val raw    = output.buffer
            val frames = output.bufferLength / channels

            val mono = ShortArray(frames) { i ->
                if (channels == 2)
                    ((raw[i * 2].toInt() + raw[i * 2 + 1].toInt()) / 2).toShort()
                else
                    raw[i]
            }

            val safeTrim = trimStart.coerceIn(0, mono.size)
            val sourceAvailable = mono.size - safeTrim
            if (sourceAvailable <= 0) return

            val resampled: ShortArray = if (resampleRatio != 1.0) {
                val outSize = (sourceAvailable / resampleRatio).toInt().coerceAtLeast(0)
                if (outSize == 0) return
                ShortArray(outSize) { i ->
                    val srcPos = safeTrim + i * resampleRatio
                    val idx    = srcPos.toInt()
                    val frac   = srcPos - idx
                    val s0     = mono.getOrElse(idx)     { 0 }.toInt()
                    val s1     = mono.getOrElse(idx + 1) { 0 }.toInt()
                    (s0 + frac * (s1 - s0)).toInt().toShort()
                }
            } else {
                mono.copyOfRange(safeTrim, mono.size)
            }

            var written = 0
            while (written < resampled.size) {
                if (pendingSeekSample >= 0L || playbackState == PlaybackState.STOPPED) return
                val space = ringWritable()
                if (space == 0) { Thread.sleep(1); continue }
                val n = min(space, resampled.size - written)
                for (i in 0 until n)
                    ring[(writeHead + i) and ringMask] = resampled[written + i]
                writeHead = (writeHead + n) and (ringCapacity * 2 - 1)
                written += n
            }
        }

        // Returns how many samples are available to read from the ring.
        private fun ringReadable(): Int {
            val w = writeHead; val r = readHead
            return if (w >= r) w - r else (ringCapacity * 2 - r) + w
        }

        // Returns how many samples can still be written before the ring is full.
        private fun ringWritable(): Int = ringCapacity - ringReadable()

        // Reads one sample from the ring and signals the decode thread if space opened up.
        private fun ringRead(): Short {
            val s = ring[readHead and ringMask]
            readHead = (readHead + 1) and (ringCapacity * 2 - 1)
            samplesConsumed++
            if (ringWritable() >= FRAME_SIZE && streamLock.tryLock()) {
                try { bufferHasSpace.signal() } finally { streamLock.unlock() }
            }
            return s
        }

        // Total sample count — exact for short files, estimated via total_ms() for streams.
        val totalSamples: Int get() = if (isStreaming) estimatedTotalSamples else samples.size

        // Playback progress as a 0.0–1.0 fraction.
        val progress: Double get() = if (isStreaming) {
            if (estimatedTotalSamples <= 0) 0.0 else samplesConsumed.toDouble() / estimatedTotalSamples
        } else {
            if (samples.isEmpty()) 0.0 else cursor.toDouble() / samples.size
        }

        // True when there is no more audio to read. For streams, waits for the ring to drain too.
        val isFinished: Boolean get() =
            if (isStreaming) decodeFinished && ringReadable() == 0
            else playbackState == PlaybackState.PLAYING && cursor >= samples.size

        // Samples left to read this frame. Returns 0 during a seek so the mixer stays silent.
        val remaining: Int get() =
            if (isStreaming) ringReadable()
            else samples.size - cursor

        // Returns the next sample to mix.
        fun readNext(): Short =
            if (isStreaming) ringRead()
            else if (cursor < samples.size) samples[cursor++] else 0

        // Jumps playback to the given fraction. For streams: resets the ring, posts a seek
        // request, and sets state to LOADING so the mixer stays silent until the buffer refills.
        fun seekTo(fraction: Double) {
            if (!isStreaming) {
                cursor = (fraction * samples.size).toInt().coerceIn(0, samples.size)
                return
            }
            if (estimatedTotalSamples <= 0) return

            val target = (fraction * estimatedTotalSamples).toLong()
                .coerceIn(0, estimatedTotalSamples.toLong())

            streamLock.withLock {
                playbackState = PlaybackState.LOADING
                decodeFinished = false
                writeHead = 0
                readHead  = 0
                samplesConsumed = target.toInt()
                pendingSeekSample = target
                bufferHasSpace.signalAll()
            }
        }

        // Restarts playback from the beginning. Called from the audio thread so must not block.
        fun repeatSound() {
            if (isStreaming) {
                streamLock.withLock {
                    playbackState = PlaybackState.LOADING
                    decodeFinished = false
                    writeHead = 0
                    readHead  = 0
                    samplesConsumed = 0
                    pendingSeekSample = 0L
                    bufferHasSpace.signalAll()
                }
            } else {
                cursor = 0
            }
        }

        // Signals the decode thread to exit cleanly.
        fun stop() {
            streamLock.withLock {
                playbackState = PlaybackState.STOPPED
                bufferHasSpace.signalAll()
            }
        }
    }

    // Returns the smallest power of two >= n.
    private fun nextPow2(n: Int): Int {
        var v = n - 1
        v = v or (v ushr 1); v = v or (v ushr 2); v = v or (v ushr 4)
        v = v or (v ushr 8); v = v or (v ushr 16)
        return v + 1
    }
}

enum class PlaybackState { LOADING, PLAYING, STOPPED }