package org.wolmics.soundboardplus.config

import kotlinx.serialization.Serializable

@Serializable
data class SoundData(
    val category: String = "",
    var favorite: Boolean = false,
    var keybind: Int = -1
)