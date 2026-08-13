package org.wolmics.soundboardplus.config

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.fabricmc.loader.api.FabricLoader
import org.wolmics.soundboardplus.SimpleSoundboardClient
import java.io.File

object SoundboardConfig {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val configFile = File(FabricLoader.getInstance().configDir.toFile(), "soundboardplus.json")
    private val oldConfigFile = File(FabricLoader.getInstance().configDir.toFile(), "simplesoundboard.json")

    var data: ConfigData = ConfigData()

    init {
        load()
    }

    operator fun get(sound: Int): SoundData? {
        return data.sounds.find { it.id == sound }
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
        if (oldConfigFile.exists()) migrateOldConfig()
        if (!configFile.exists()) return

        try {
            data = json.decodeFromString<ConfigData>(configFile.readText())
            refresh()
        } catch(e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: SerializationException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun JsonObject?.orEmpty() = this ?: JsonObject(emptyMap())
    private fun JsonObject.bool(key: String, default: Boolean) = this[key]?.jsonPrimitive?.booleanOrNull ?: default
    private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: -1
    private fun JsonObject.float(key: String, default: Float) = this[key]?.jsonPrimitive?.floatOrNull ?: default
    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull ?: ""

    private fun migrateOldConfig() {
        val root = json.parseToJsonElement(oldConfigFile.readText()).jsonObject
        val oldCategories = root["categories"]?.jsonObject.orEmpty()

        val newData = ConfigData().apply {
            playWhileMuted = root.bool("playWhileMuted", true)
            playOnlyOne = root.bool("playOnlyOne", true)
            showProgressBar = root.bool("showProgressBar", true)
            saveLastCategory = root.bool("saveLastCategory", true)
            localVolume = root.float("localVolume", 1.0f)
            playerVolume = root.float("playerVolume", 1.0f)
            ytDlpPath = root.str("ytDlpPath")
            ffmpegPath = root.str("ffmpegPath")
        }

        for ((categoryName, categoryElement) in oldCategories) {
            newData.categories.add(categoryName)

            val sounds = categoryElement.jsonObject["sounds"]?.jsonObject.orEmpty()
            for ((soundName, soundElement) in sounds) {
                val sound = soundElement.jsonObject

                val filePath = if (categoryName == "default")
                    File(SimpleSoundboardClient.soundDir, soundName).path
                else
                    File(SimpleSoundboardClient.soundDir, "$categoryName/$soundName").path

                newData.sounds.add(
                    SoundData(newData.nextId++, soundName.removeSuffix(".mp3"), filePath, categoryName, sound.bool("favorite", false), sound.int("keybind"))
                )
            }
        }

        data = newData
        save()
        oldConfigFile.delete()
    }

    fun refresh() {
        val soundDir = SimpleSoundboardClient.soundDir
        if (!soundDir.exists() || !soundDir.isDirectory) return

        // --- 1. Collect what actually exists on disk: category -> (fileName -> File) ---
        val diskSounds = mutableMapOf<String, MutableMap<String, File>>()

        soundDir.listFiles()?.forEach { entry ->
            if (entry.isDirectory) {
                val sounds = diskSounds.getOrPut(entry.name) { mutableMapOf() }
                entry.listFiles { _, name -> name.endsWith(".mp3") }
                    ?.forEach { file -> sounds[file.name] = file }
            } else if (entry.name.endsWith(".mp3")) {
                diskSounds.getOrPut("default") { mutableMapOf() }[entry.name] = entry
            }
        }
        val diskCategories = diskSounds.keys

        // --- 2. Remove categories no longer on disk ---
        data.categories.removeAll { it !in diskSounds }

        // --- 3. Remove sounds no longer on disk ---
        data.sounds.removeAll { sound ->
            val fileName = File(sound.filePath).name
            diskSounds[sound.category]?.containsKey(fileName) != true
        }

        // --- 4. Add new categories ---
        val existingCategories = data.categories.mapTo(mutableSetOf()) { it }
        diskCategories.forEach { categoryName ->
            if (categoryName !in existingCategories) {
                data.categories.add(categoryName)
            }
        }

        // --- 5. Add new sounds ---
        val existingSounds = data.sounds.mapTo(mutableSetOf()) { it.category to File(it.filePath).name }
        diskSounds.forEach { (categoryName, sounds) ->
            sounds.forEach { (fileName, file) ->
                if (categoryName to fileName !in existingSounds) {
                    data.sounds.add(SoundData(data.nextId++, fileName.removeSuffix(".mp3"), file.path, categoryName))
                }
            }
        }

        save()
    }

    fun createNewCategory(name: String) {
        File(SimpleSoundboardClient.soundDir, name).mkdir()
        data.categories.add(name)
        save()
    }

    fun renameSound(id: Int, newName: String): Boolean {
        val soundData = get(id) ?: return false

        val originalFile = File(soundData.filePath)
        val newFile = File(originalFile.parentFile, "$newName.mp3")

        if (!originalFile.renameTo(newFile)) return false

        soundData.filePath = newFile.path
        soundData.name = newName

        save()
        return true
    }

    fun changeSoundCategory(sound: SoundData, category: String?): Boolean {
        val newCategory = category ?: "default"
        if (sound.category == newCategory || newCategory !in data.categories) return false

        val catDir = if (newCategory != "default") File(SimpleSoundboardClient.soundDir, newCategory) else SimpleSoundboardClient.soundDir
        val dstFile = File(catDir, "${sound.name}.mp3")

        if (!File(sound.filePath).renameTo(dstFile)) return false

        sound.filePath = dstFile.path
        sound.category = newCategory

        save()
        return true
    }

    fun deleteCategory(category: String) {
        if (!data.categories.remove(category)) return
        data.sounds.removeIf { it.category == category }
        File(SimpleSoundboardClient.soundDir, category).deleteRecursively()
        save()
    }

    fun deleteSound(id: Int) {
        val sound: SoundData = get(id) ?: return
        data.sounds.remove(sound)
        File(sound.filePath).delete()
        save()
    }
}