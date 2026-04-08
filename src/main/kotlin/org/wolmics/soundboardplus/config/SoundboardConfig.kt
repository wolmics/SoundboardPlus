package org.wolmics.soundboardplus.config

import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import org.wolmics.soundboardplus.SimpleSoundboardClient
import java.io.File

object SoundboardConfig {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val configFile = File(FabricLoader.getInstance().configDir.toFile(), "simplesoundboard.json")

    var data: ConfigData = ConfigData()

    init {
        load()
    }

    operator fun get(filename: String): SoundData {
        for ((_, category) in data.categories) {
            category.sounds[filename]?.let { return it }
        }
        return SoundData()
    }

    fun save() {
        try {
            if (!configFile.parentFile.exists()) configFile.parentFile.mkdirs()
            configFile.writeText(json.encodeToString(data))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun load() {
        if (!configFile.exists()) return
        try {
            data = json.decodeFromString<ConfigData>(configFile.readText())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun refresh() {
        val soundDir = SimpleSoundboardClient.soundDir ?: return
        if (!soundDir.exists() || !soundDir.isDirectory) return

        // --- 1. Collect what actually exists on disk ---
        val diskCategories = mutableSetOf<String>()
        val diskSounds = mutableMapOf<String, MutableSet<String>>()

        soundDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                diskCategories.add(entry.name)
                val sounds = diskSounds.getOrPut(entry.name) { mutableSetOf() }
                entry.listFiles { _, name -> name.endsWith(".mp3") }
                    ?.forEach { file -> sounds.add(file.name) }
            } else if (entry.name.endsWith(".mp3")) {
                diskCategories.add("default")
                diskSounds.getOrPut("default") { mutableSetOf() }.add(entry.name)
            }
        }

        // --- 2. Add anything new ---
        diskCategories.forEach { categoryName ->
            val category = data.categories.getOrPut(categoryName) {
                CategoryData(category = categoryName)
            }
            diskSounds[categoryName]?.forEach { soundName ->
                category.sounds.getOrPut(soundName) { SoundData() }
            }
        }

        // --- 3. Remove categories that no longer exist on disk ---
        data.categories.keys.retainAll(diskCategories)

        // --- 4. Remove sounds that no longer exist in each category ---
        data.categories.forEach { (categoryName, category) ->
            category.sounds.keys.retainAll((diskSounds[categoryName] ?: emptySet()).toSet())
        }

        save()
    }

    fun createNewCategory(name: String): CategoryData {
        val category = CategoryData(category = name)
        data.categories[name] = category
        File(SimpleSoundboardClient.soundDir, name).mkdir()
        return category
    }

    fun deleteSound(file: File) {
        val sound: SoundData = get(file.name)
        data.categories[sound.category]?.sounds?.remove(file.name)
        file.delete()
    }
}