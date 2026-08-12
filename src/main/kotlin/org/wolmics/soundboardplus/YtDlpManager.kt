package org.wolmics.soundboardplus

import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.toasts.TutorialToast
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.wolmics.soundboardplus.config.SoundboardConfig
import org.wolmics.soundboardplus.gui.SoundboardScreen
import org.wolmics.soundboardplus.util.ToastManager
import java.awt.Color
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class YtDlpManager {

    private val isWindows = Util.getPlatform().name.contains("windows", ignoreCase = true)
    private val logger: Logger = LoggerFactory.getLogger("YtDlpManager")

    val ytDlpFile: File
        get() = File(SoundboardConfig.data.ytDlpPath).takeIf { it.exists() && it.isFile() && it.canExecute() }
            ?: File(SimpleSoundboardClient.modDependencyDir, if (isWindows) "yt-dlp.exe" else "yt-dlp")

    private val ytDlpUrl = if (isWindows)
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    else
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    private val ytDlpNightlyUrl = if (isWindows)
        "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp.exe"
    else
        "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/yt-dlp"

    private val ffmpegDir = File(SimpleSoundboardClient.modDependencyDir, "ffmpeg")

    val ffmpegFile: File
        get() = File(SoundboardConfig.data.ffmpegPath).takeIf { it.exists() && it.isFile() && it.canExecute() }
            ?: File(ffmpegDir, if (isWindows) "ffmpeg.exe" else "ffmpeg")

    private val ffmpegUrl = if (isWindows)
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
    else
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl-shared.tar.xz"


    val logFile = File(SimpleSoundboardClient.modDependencyDir, "yt-dlp.log")
    private val maxLogFileBytes = 5_000_000L // ~5MB, then start over

    // ---------------------------
    // Install / uninstall / update / status
    // ---------------------------

    fun isYtDlpInstalled() = ytDlpFile.exists() && ytDlpFile.canExecute()
    fun isFfmpegInstalled() = ffmpegFile.exists() && ffmpegFile.canExecute()

    fun ytDlpVersion(): String? = readVersion(ytDlpFile, "--version", Regex("""([\d.]+)"""))
    fun ffmpegVersion(): String? = readVersion(ffmpegFile, "-version", Regex("""ffmpeg version (\S+)"""))

    fun ensureBinariesPresent(toast: TutorialToast? = null): Boolean {
        val ytDlpOk = isYtDlpInstalled() || installYtDlp(toast)
        val ffmpegOk = isFfmpegInstalled() || installFfmpeg(toast)
        return ytDlpOk && ffmpegOk
    }

    fun installYtDlp(toast: TutorialToast? = null): Boolean {
        return try {
            ToastManager.updateTextAsync(toast, Component.literal("Downloading yt-dlp..."))
            downloadFile(ytDlpUrl, ytDlpFile)
            ytDlpFile.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            failure(toast, "message.simplesoundboard.youtube.install_failed")
            t.printStackTrace()
            false
        }
    }

    fun installFfmpeg(toast: TutorialToast? = null): Boolean {
        return try {
            ToastManager.updateTextAsync(toast,Component.literal("Downloading FFmpeg..."))
            if (!ffmpegDir.exists()) ffmpegDir.mkdirs()

            val archiveFile = File(ffmpegDir, if (isWindows) "ffmpeg.zip" else "ffmpeg.tar.xz")
            downloadFile(ffmpegUrl, archiveFile)

            if (isWindows) extractZip(archiveFile, ffmpegDir) else extractTarXz(archiveFile, ffmpegDir)
            archiveFile.delete()

            ffmpegFile.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            failure(toast, "message.simplesoundboard.ffmpeg.install_failed")
            t.printStackTrace()
            false
        }
    }

    fun uninstallYtDlp(): Boolean = ytDlpFile.delete()
    fun uninstallFfmpeg(): Boolean = ffmpegDir.deleteRecursively()

    fun uninstallAll(): Boolean {
        val a = uninstallYtDlp()
        val b = uninstallFfmpeg()
        return a && b
    }

    fun updateYtDlp(toast: TutorialToast? = null): Boolean {
        uninstallYtDlp()
        return installYtDlp(toast)
    }

    fun updateFfmpeg(toast: TutorialToast? = null): Boolean {
        uninstallFfmpeg()
        return installFfmpeg(toast)
    }

    fun updateYtDlpNightly(toast: TutorialToast? = null): Boolean {
        return try {
            ToastManager.updateTextAsync(toast, Component.literal("Downloading yt-dlp (nightly)......"))
            uninstallYtDlp()
            downloadFile(ytDlpNightlyUrl, ytDlpFile)
            ytDlpFile.setExecutable(true, false)
            ToastManager.hideToast(toast, 2000L)
            true
        } catch (t: Throwable) {
            failure(toast, "message.simplesoundboard.youtube.update_nightly_failed")
            t.printStackTrace()
            false
        }
    }

    fun setFfmpegPath(path: String): Boolean {
        val file = validateExecutable(
            path,
            expectedName = "ffmpeg",
            versionArg = "-version",
            versionRegex = Regex("""ffmpeg version (\S+)""")
        ) ?: return false

        SoundboardConfig.data.ffmpegPath = file.path
        return true
    }

    fun setYtDlpPath(path: String): Boolean {
        val file = validateExecutable(
            path,
            expectedName = "yt-dlp",
            versionArg = "--version",
            versionRegex = Regex("""([\d.]+)""")
        ) ?: return false

        SoundboardConfig.data.ytDlpPath = file.path

        return true
    }


    // ---------------------------
    // Download into sound dir
    // ---------------------------

    fun downloadUrlIntoSoundDir(url: String, category: String?): Pair<Boolean, String> {
        if (url.isBlank()) return Pair(false, "message.simplesoundboard.empty_url")
        // local var, not a shared field - each call gets its own toast,
        // so two downloads running at once can't clobber each other's
        val toast = ToastManager.createProgressToast(Component.literal("Preparing Download..."))

        if (!ensureBinariesPresent(toast)) {
            return failure(toast, "message.simplesoundboard.binaries_missing")
        }

        return try {
            val proc = startDownloadProcess(url, category)
            streamProcessOutput(proc, toast)
            awaitProcess(proc, toast)
        } catch (t: Throwable) {
            t.printStackTrace()
            failure(toast, "Exception: ${t.message}")
        }
    }

    private fun startDownloadProcess(url: String, category: String?): Process {
        val soundDir = SimpleSoundboardClient.soundDir.also { if (!it.exists()) it.mkdirs() }

        val outputPattern = if (category == null)
            File(soundDir, "%(title)s.%(ext)s").absolutePath
        else
            File(soundDir, "/$category/%(title)s.%(ext)s").absolutePath

        val args = listOf(
            ytDlpFile.absolutePath,
            "--ffmpeg-location", ffmpegFile.absolutePath,
            "-x", "--audio-format", "mp3",
            "-o", outputPattern,
            url
        )

        return ProcessBuilder(args)
            .directory(soundDir)
            .redirectErrorStream(true)
            .start()
    }

    private fun streamProcessOutput(proc: Process, toast: TutorialToast) {
        ToastManager.updateTextAsync(toast, Component.literal("Downloading..."))
        toast.updateProgress(0.20f)

        logFile.parentFile?.mkdirs()
        if (logFile.exists() && logFile.length() > maxLogFileBytes) logFile.delete()

        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            FileWriter(logFile, true).buffered().use { log ->
                log.appendLine("----- ${LocalDateTime.now()} -----")
                reader.forEachLine { line ->
                    log.appendLine(line)
                    when {
                        line.startsWith("[download]") -> handleDownloadLine(line, toast)
                        line.startsWith("[ExtractAudio]") ->
                            ToastManager.updateTextAsync(toast, Component.literal("Converting to mp3..."))
                    }
                    logger.debug(line)
                }
            }
        }
    }

    private fun handleDownloadLine(line: String, toast: TutorialToast) {
        val percent = Regex("""([\d.]+)%""")
            .find(line)
            ?.groupValues?.get(1)
            ?.toFloatOrNull()
            ?.div(100f)
            ?: return

        // first 20% is reserved for the "preparing" phase
        if (percent > 0.20f) toast.updateProgress(percent)
    }

    private fun awaitProcess(proc: Process, toast: TutorialToast): Pair<Boolean, String> {
        val finished = proc.waitFor(3, TimeUnit.MINUTES)

        if (!finished) {
            proc.destroyForcibly()
            return failure(toast, "message.simplesoundboard.youtube.timeout")
        }

        return if (proc.exitValue() == 0) {
            ToastManager.updateTextAsync(toast, Component.literal("Download Complete!").withStyle(ChatFormatting.BOLD).withColor(Color(0, 210, 0).rgb))
            ToastManager.hideToast(toast, 2000L)

            val screen = Minecraft.getInstance().screen
            if (screen is SoundboardScreen) screen.scanSounds()

            Pair(true, "message.simplesoundboard.download_completed")
        } else {
            failure(toast, "message.simplesoundboard.youtube.exit_code")
        }
    }

    // ---------------------------
    // Shared helpers
    // ---------------------------

    private fun downloadFile(urlStr: String, dest: File) {
        val conn = (URI(urlStr).toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "SimpleSoundboard-Downloader")
        }

        conn.connect()
        val code = conn.responseCode
        if (code >= 400) {
            conn.disconnect()
            throw RuntimeException("Failed to download from $urlStr: HTTP $code")
        }

        BufferedInputStream(conn.inputStream).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }

        conn.disconnect()
    }

    private fun extractZip(zipFile: File, destDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                if (!entry.isDirectory && (name.contains("/bin/") || name.startsWith("bin/"))) {
                    val fileName = name.substringAfterLast('/')
                    zip.getInputStream(entry).use { input ->
                        File(destDir, fileName).outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun extractTarXz(archiveFile: File, destDir: File) {
        val listProc = ProcessBuilder("tar", "-tJf", archiveFile.absolutePath).start()
        val rootDir = BufferedReader(InputStreamReader(listProc.inputStream)).readLine()?.substringBefore('/') ?: ""
        listProc.waitFor()

        if (rootDir.isNotEmpty()) {
            ProcessBuilder(
                "tar", "-xJf", archiveFile.absolutePath,
                "--strip-components=2",
                "-C", destDir.absolutePath,
                "$rootDir/bin"
            ).start().waitFor(2, TimeUnit.MINUTES)
        }
    }

    private fun readVersion(binary: File, versionArg: String, regex: Regex): String? {
        if (!binary.exists() || !binary.canExecute()) return null
        return try {
            val proc = ProcessBuilder(binary.absolutePath, versionArg).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            proc.waitFor(10, TimeUnit.SECONDS)
            regex.find(output)?.groupValues?.get(1)
        } catch (t: Throwable) {
            null
        }
    }

    private fun validateExecutable(path: String, expectedName: String, versionArg: String, versionRegex: Regex): File? {
        val file = File(path)

        if (!file.exists()) return null
        if (file.nameWithoutExtension != expectedName) return null
        if (!file.canExecute()) return null
        readVersion(file, versionArg, versionRegex) ?: return null

        return file
    }

    private fun failure(toast: TutorialToast?, msg: String): Pair<Boolean, String> {
        ToastManager.updateTextAsync(toast, Component.literal(msg).withStyle(ChatFormatting.RED))
        ToastManager.hideToast(toast, 2000L)
        return Pair(false, msg)
    }
}