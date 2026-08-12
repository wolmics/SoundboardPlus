package org.wolmics.soundboardplus.config

import kotlinx.serialization.Serializable

@Serializable
data class SoundData(
    val id: Int,
    var name: String,
    var filePath: String,
    var category: String = "default",

    // Sound specific data
    var favorite: Boolean = false,
    var keybind: Int = -1,

    // Stats
    var playCount: Int = 0
)