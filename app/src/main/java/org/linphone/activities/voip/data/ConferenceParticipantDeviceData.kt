/*
 * Copyright (c) 2010-2021 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.activities.voip.data

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.MutableLiveData
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.contact.GenericContactData
import org.linphone.core.MediaDirection
import org.linphone.core.ParticipantDevice
import org.linphone.core.ParticipantDeviceListenerStub
import org.linphone.core.tools.Log

class ConferenceParticipantDeviceData(
    private val participantDevice: ParticipantDevice,
    val isMe: Boolean
) :
    GenericContactData(participantDevice.address) {
    val videoEnabled = MutableLiveData<Boolean>()

    val activeSpeaker = MutableLiveData<Boolean>()

    val isInConference = MutableLiveData<Boolean>()

    private val listener = object : ParticipantDeviceListenerStub() {
        override fun onIsSpeakingChanged(
            participantDevice: ParticipantDevice,
            isSpeaking: Boolean
        ) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] is ${if (isSpeaking) "speaking" else "not speaking"}")
            activeSpeaker.value = isSpeaking
        }

        override fun onConferenceJoined(participantDevice: ParticipantDevice) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] has joined the conference")
            isInConference.value = true
        }

        override fun onConferenceLeft(participantDevice: ParticipantDevice) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] has left the conference")
            isInConference.value = false
        }

        override fun onVideoDirectionChanged(
            participantDevice: ParticipantDevice,
            direction: MediaDirection?
        ) {
            Log.i("[Conference Participant Device] Participant [${participantDevice.address.asStringUriOnly()}] video stream direction changed: $direction")
            videoEnabled.value = direction == MediaDirection.SendOnly || direction == MediaDirection.SendRecv
        }
    }

    init {
        Log.i("[Conference Participant Device] Created device width Address [${participantDevice.address.asStringUriOnly()}], is it myself? $isMe")
        participantDevice.addListener(listener)

        activeSpeaker.value = false

        // TODO: What happens if we have disabled video locally?
        videoEnabled.value = participantDevice.videoDirection == MediaDirection.SendOnly || participantDevice.videoDirection == MediaDirection.SendRecv

        isInConference.value = participantDevice.isInConference
    }

    override fun destroy() {
        participantDevice.removeListener(listener)

        super.destroy()
    }

    fun switchCamera() {
        coreContext.switchCamera()
    }

    fun isSwitchCameraAvailable(): Boolean {
        return isMe && coreContext.showSwitchCameraButton()
    }

    fun setTextureView(textureView: TextureView) {
        if (textureView.isAvailable) {
            Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
            if (isMe) { // TODO: remove
                coreContext.core.nativePreviewWindowId = textureView
            } else {
                participantDevice.nativeVideoWindowId = textureView
            }
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    Log.i("[Conference Participant Device] Setting textureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}]")
                    if (isMe) { // TODO: remove
                        coreContext.core.nativePreviewWindowId = textureView
                    } else {
                        participantDevice.nativeVideoWindowId = textureView
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture,
                    width: Int,
                    height: Int
                ) { }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.w("[Conference Participant Device] TextureView [$textureView] for participant [${participantDevice.address.asStringUriOnly()}] has been destroyed")
                    participantDevice.nativeVideoWindowId = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
            }
        }
    }
}
