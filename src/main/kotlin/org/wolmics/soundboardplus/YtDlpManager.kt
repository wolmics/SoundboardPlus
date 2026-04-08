package org.wolmics.soundboardplus

import net.minecraft.client.toast.TutorialToast
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Util
import org.wolmics.soundboardplus.util.ToastManager
import java.awt.Color
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

class YtDlpManager {

    private val isWindows = Util.getOperatingSystem().getName().contains("windows", ignoreCase = true)
    private val binaryName = if (isWindows) "yt-dlp.exe" else "yt-dlp"
    private val downloadUrl = if (isWindows)
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    else
        "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"

    private val ffmpegDir = File(SimpleSoundboardClient.modDependencyDir.path + "/ffmpeg/")
    private val ffmpegBinaryName = if (isWindows) "ffmpeg.exe" else "ffmpeg"
    private val ffmpegDownloadUrl = if (isWindows)
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl-shared.zip"
    else
        "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl-shared.tar.xz"

    private lateinit var toast: TutorialToast

    private fun binFile(): File {
        if (!SimpleSoundboardClient.modDependencyDir.exists()) SimpleSoundboardClient.modDependencyDir.mkdirs()
        return File(SimpleSoundboardClient.modDependencyDir, binaryName)
    }

    private fun ffmpegBinFile(): File {
        if (!SimpleSoundboardClient.modDependencyDir.exists()) SimpleSoundboardClient.modDependencyDir.mkdirs()
        if (!ffmpegDir.exists()) ffmpegDir.mkdirs()
        return File(ffmpegDir, ffmpegBinaryName)
    }

    @Synchronized
    fun ensureBinariesPresent(): Boolean {
        return ensureYtDlpPresent() && ensureFfmpegPresent()
    }

    @Synchronized
    fun ensureYtDlpPresent(): Boolean {
        val bin = binFile()
        if (bin.exists() && bin.canExecute()) return true

        return try {
            ToastManager.changeToastText(toast, Text.literal("Downloading yt-dlp..."))

            downloadBinary(downloadUrl, bin)
            bin.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            ToastManager.changeToastText(toast, Text.literal("Failed to download yt-dlp.").withColor(0xFFFF0000.toInt()))
            t.printStackTrace()
            false
        }
    }

    @Synchronized
    fun ensureFfmpegPresent(): Boolean {
        val bin = ffmpegBinFile()
        if (bin.exists() && bin.canExecute()) return true

        return try {
            ToastManager.changeToastText(toast, Text.literal("Downloading FFmpeg..."))
            val archiveName = if (isWindows) "ffmpeg.zip" else "ffmpeg.tar.xz"
            val archiveFile = File(SimpleSoundboardClient.modDependencyDir, archiveName)
            downloadBinary(ffmpegDownloadUrl, archiveFile)

            if (isWindows) {
                extractZip(archiveFile, ffmpegDir)
            } else {
                extractTarXz(archiveFile, ffmpegDir)
            }

            archiveFile.delete()
            bin.setExecutable(true, false)
            true
        } catch (t: Throwable) {
            ToastManager.changeToastText(toast, Text.literal("Failed to download FFmpeg.").withColor(0xFFFF0000.toInt()))
            t.printStackTrace()
            false
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        java.util.zip.ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                if (!entry.isDirectory && (name.contains("/bin/") || name.startsWith("bin/"))) {
                    val fileName = name.substringAfterLast('/')
                    val outputFile = File(destDir, fileName)
                    zip.getInputStream(entry).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun extractTarXz(archiveFile: File, destDir: File) {
        try {
            // Find the root directory name in the tarball
            val pbList = ProcessBuilder("tar", "-tJf", archiveFile.absolutePath)
            val procList = pbList.start()
            val firstEntry = BufferedReader(InputStreamReader(procList.inputStream)).readLine()
            val rootDir = firstEntry?.substringBefore('/') ?: ""
            procList.waitFor()

            if (rootDir.isNotEmpty()) {
                // Extract everything from the 'bin' directory to destDir, flattening it
                val pb = ProcessBuilder(
                    "tar", "-xJf", archiveFile.absolutePath,
                    "--strip-components=2",
                    "-C", destDir.absolutePath,
                    "$rootDir/bin"
                )
                pb.start().waitFor(2, TimeUnit.MINUTES)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun downloadBinary(urlStr: String, dest: File) {
        val url = URI(urlStr).toURL()
        val conn = (url.openConnection() as HttpURLConnection).apply {
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
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        conn.disconnect()
    }

    fun downloadUrlIntoSoundDir(url: String, category: String?): Pair<Boolean, String> {
        if (url.isBlank()) return failure("message.simplesoundboard.empty_url")

        toast = ToastManager.createProgressToast(Text.literal("Preparing Download..."))

        if (!ensureBinariesPresent()) return failure("message.simplesoundboard.binaries_missing")

        return try {
            val proc = startDownloadProcess(url, category)
            streamProcessOutput(proc)
            awaitProcess(proc)
        } catch (t: Throwable) {
            t.printStackTrace()
            failure("Exception: ${t.message}")
        }
    }

    private fun startDownloadProcess(url: String, category: String?): Process {
        toast.setProgress(0.20f)

        val soundDir = SimpleSoundboardClient.soundDir.also { if (!it.exists()) it.mkdirs() }

        var outputPattern: String
        if (category == null) {
            outputPattern = File(soundDir, "%(title)s.%(ext)s").absolutePath
        } else {
            outputPattern = File(soundDir, "/$category/%(title)s.%(ext)s").absolutePath
        }

        val args = listOf(
            binFile().absolutePath,
            "--ffmpeg-location", ffmpegBinFile().absolutePath,
            "-x", "--audio-format", "mp3",
            "-o", outputPattern,
            url
        )

        return ProcessBuilder(args)
            .directory(soundDir)
            .redirectErrorStream(true)
            .start()
    }

    private fun streamProcessOutput(proc: Process) {
        ToastManager.changeToastText(toast, Text.literal("Downloading..."))

        BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
            reader.forEachLine { line ->
                when {
                    line.startsWith("[download]") -> handleDownloadLine(line)
                    line.startsWith("[ExtractAudio]") ->
                        ToastManager.changeToastText(toast, Text.literal("Converting to mp3..."))
                }
                println(line)
            }
        }
    }

    private fun handleDownloadLine(line: String) {
        val percent = Regex("""([\d.]+)%""")
            .find(line)
            ?.groupValues?.get(1)
            ?.toFloatOrNull()
            ?.div(100f)
            ?: return

        if (percent > 0.20f) toast.setProgress(percent)
    }

    private fun awaitProcess(proc: Process): Pair<Boolean, String> {
        val finished = proc.waitFor(3, TimeUnit.MINUTES)

        if (!finished) {
            proc.destroyForcibly()
            return failure("message.simplesoundboard.youtube.timeout")
        }

        ToastManager.hideTutorialToastIn(toast, 2000L)

        return if (proc.exitValue() == 0) {
            ToastManager.changeToastText(toast, Text.literal("Download Complete!")
                .formatted(Formatting.BOLD).withColor(Color(0, 210, 0).rgb))
            success("message.simplesoundboard.download_completed")
        } else {
            ToastManager.changeToastText(toast, Text.literal("Error while downloading!")
                .formatted(Formatting.RED))
            failure("message.simplesoundboard.youtube.exit_code")
        }
    }

    // Convenience aliases for readability
    private fun success(msg: String) = Pair(true, msg)
    private fun failure(msg: String) = Pair(false, msg)

}