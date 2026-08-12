package org.wolmics.soundboardplus.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    val formatVersion: String = "1.0",
    var playWhileMuted: Boolean = true,
    var playOnlyOne: Boolean = false,
    var showProgressBar: Boolean = true,
    var saveLastCategory: Boolean = true,

    var localVolume: Float = 1.0f,
    var playerVolume: Float = 1.0f,

    var nextId: Int = 1,
    val sounds: MutableList<SoundData> = mutableListOf(),
    val categories: MutableList<String> = mutableListOf(),

    var ytDlpPath: String = "",
    var ffmpegPath: String = ""
)