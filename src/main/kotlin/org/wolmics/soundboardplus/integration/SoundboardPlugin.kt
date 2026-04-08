package org.wolmics.soundboardplus.integration

import de.maxhenkel.voicechat.api.VoicechatApi
import de.maxhenkel.voicechat.api.VoicechatPlugin
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent
import de.maxhenkel.voicechat.api.events.EventRegistration
import de.maxhenkel.voicechat.api.events.MergeClientSoundEvent
import org.wolmics.soundboardplus.SimpleSoundboardClient
import org.wolmics.soundboardplus.SoundboardAudioSystem

class SoundboardPlugin : VoicechatPlugin {

    override fun getPluginId(): String = "${SimpleSoundboardClient.MOD_ID}_plugin"

    override fun initialize(api: VoicechatApi) {
        SoundboardAudioSystem.initialize(api)
    }

    override fun registerEvents(registration: EventRegistration) {
        registration.registerEvent(ClientVoicechatConnectionEvent::class.java) { event ->
            SoundboardAudioSystem.onClientConnection(event)
        }

        registration.registerEvent(MergeClientSoundEvent::class.java) { event ->
            SoundboardAudioSystem.onMergeSound(event)
        }
    }

}