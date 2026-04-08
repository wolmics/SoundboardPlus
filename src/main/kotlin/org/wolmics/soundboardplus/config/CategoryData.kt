package org.wolmics.soundboardplus.config

import kotlinx.serialization.Serializable

@Serializable
data class CategoryData(
    var category: String = "default",
    val sounds: MutableMap<String, SoundData> = mutableMapOf(),
)

