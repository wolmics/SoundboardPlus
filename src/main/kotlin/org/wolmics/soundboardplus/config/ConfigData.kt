package org.wolmics.soundboardplus.config

import kotlinx.serialization.Serializable

@Serializable
data class ConfigData(
    var playLocally: Boolean = true,
    var playWhileMuted: Boolean = true,
    var playOnlyOne: Boolean = false,
    var showProgressBar: Boolean = true,

    var localVolume: Float = 1.0f,
    var playerVolume: Float = 1.0f,
    val categories: MutableMap<String, CategoryData> = mutableMapOf()
)