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
package org.linphone.activities.voip.viewmodels

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.flexbox.FlexDirection
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.activities.voip.data.ConferenceParticipantData
import org.linphone.activities.voip.data.ConferenceParticipantDeviceData
import org.linphone.core.*
import org.linphone.core.tools.Log
import org.linphone.utils.AppUtils
import org.linphone.utils.Event
import org.linphone.utils.LinphoneUtils

class ConferenceViewModel : ViewModel() {
    val conferenceExists = MutableLiveData<Boolean>()
    val subject = MutableLiveData<String>()
    val isConferenceLocallyPaused = MutableLiveData<Boolean>()
    val isVideoConference = MutableLiveData<Boolean>()
    val isMeAdmin = MutableLiveData<Boolean>()

    val conference = MutableLiveData<Conference>()
    val conferenceParticipants = MutableLiveData<List<ConferenceParticipantData>>()
    val conferenceParticipantDevices = MutableLiveData<List<ConferenceParticipantDeviceData>>()
    val conferenceMosaicDisplayMode = MutableLiveData<Boolean>()
    val conferenceActiveSpeakerDisplayMode = MutableLiveData<Boolean>()

    val flexboxLayoutDirection = MutableLiveData<Int>()

    val isRecording = MutableLiveData<Boolean>()
    val isRemotelyRecorded = MutableLiveData<Boolean>()

    val participantAdminStatusChangedEvent: MutableLiveData<Event<ConferenceParticipantData>> by lazy {
        MutableLiveData<Event<ConferenceParticipantData>>()
    }

    val maxParticipantsForMosaicLayout = corePreferences.maxConferenceParticipantsForMosaicLayout

    private val conferenceListener = object : ConferenceListenerStub() {
        override fun onParticipantAdded(conference: Conference, participant: Participant) {
            Log.i("[Conference] Participant added: ${participant.address.asStringUriOnly()}")
            updateParticipantsList(conference)

            val count = conferenceParticipants.value.orEmpty().size
            if (count > maxParticipantsForMosaicLayout) {
                Log.w("[Conference] More than $maxParticipantsForMosaicLayout participants ($count), forcing active speaker layout")
                conferenceMosaicDisplayMode.value = false
                conferenceActiveSpeakerDisplayMode.value = true
            }
        }

        override fun onParticipantRemoved(conference: Conference, participant: Participant) {
            Log.i("[Conference] Participant removed: ${participant.address.asStringUriOnly()}")
            updateParticipantsList(conference)
        }

        override fun onParticipantDeviceAdded(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device added: ${participantDevice.address.asStringUriOnly()}")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantDeviceRemoved(
            conference: Conference,
            participantDevice: ParticipantDevice
        ) {
            Log.i("[Conference] Participant device removed: ${participantDevice.address.asStringUriOnly()}")
            updateParticipantsDevicesList(conference)
        }

        override fun onParticipantAdminStatusChanged(
            conference: Conference,
            participant: Participant
        ) {
            Log.i("[Conference] Participant admin status changed")
            isMeAdmin.value = conference.me.isAdmin
            updateParticipantsList(conference)
            val participantData = conferenceParticipants.value.orEmpty().find { data -> data.participant.address.weakEqual(participant.address) }
            if (participantData != null) {
                participantAdminStatusChangedEvent.value = Event(participantData)
            } else {
                Log.w("[Conference] Failed to find participant [${participant.address.asStringUriOnly()}] in conferenceParticipants list")
            }
        }

        override fun onSubjectChanged(conference: Conference, subject: String) {
            Log.i("[Conference] Subject changed: $subject")
            this@ConferenceViewModel.subject.value = subject
        }

        override fun onParticipantDeviceJoined(conference: Conference, device: ParticipantDevice) {
            if (conference.isMe(device.address)) {
                Log.i("[Conference] Entered conference")
                isConferenceLocallyPaused.value = false
            }
        }

        override fun onParticipantDeviceLeft(conference: Conference, device: ParticipantDevice) {
            if (conference.isMe(device.address)) {
                Log.i("[Conference] Left conference")
                isConferenceLocallyPaused.value = true
            }
        }
    }

    private val listener = object : CoreListenerStub() {
        override fun onConferenceStateChanged(
            core: Core,
            conference: Conference,
            state: Conference.State
        ) {
            Log.i("[Conference] Conference state changed: $state")
            isVideoConference.value = conference.currentParams.isVideoEnabled

            when (state) {
                Conference.State.Instantiated -> {
                    initConference(conference)
                }
                Conference.State.Created -> {
                    initConference(conference)
                    configureConference(conference)
                }
                Conference.State.TerminationPending -> {
                    terminateConference(conference)
                }
                else -> {}
            }

            updateConferenceLayout(conference)
        }
    }

    init {
        coreContext.core.addListener(listener)

        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
        conferenceMosaicDisplayMode.value = false
        conferenceActiveSpeakerDisplayMode.value = false

        flexboxLayoutDirection.value = FlexDirection.COLUMN

        subject.value = AppUtils.getString(R.string.conference_default_title)

        val conference = coreContext.core.conference ?: coreContext.core.currentCall?.conference
        if (conference != null) {
            Log.i("[Conference] Found an existing conference: $conference")
            initConference(conference)
            configureConference(conference)
        }
    }

    override fun onCleared() {
        coreContext.core.removeListener(listener)
        conference.value?.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)

        super.onCleared()
    }

    fun pauseConference() {
        Log.i("[Conference] Leaving conference temporarily")
        conference.value?.leave()
    }

    fun resumeConference() {
        Log.i("[Conference] Entering conference again")
        conference.value?.enter()
    }

    fun toggleRecording() {
        if (conference.value?.isRecording == true) {
            Log.i("[Conference] Stopping conference recording")
            conference.value?.stopRecording()
        } else {
            val path = LinphoneUtils.getRecordingFilePathForConference()
            Log.i("[Conference] Starting recording in file $path")
            conference.value?.startRecording(path)
        }
        isRecording.value = conference.value?.isRecording
    }

    fun initConference(conference: Conference) {
        conferenceExists.value = true
        this@ConferenceViewModel.conference.value = conference
        conference.addListener(conferenceListener)
        isRecording.value = conference.isRecording
    }

    fun configureConference(conference: Conference) {
        updateParticipantsList(conference)
        updateParticipantsDevicesList(conference)

        isConferenceLocallyPaused.value = !conference.isIn
        isMeAdmin.value = conference.me.isAdmin
        isVideoConference.value = conference.currentParams.isVideoEnabled
        subject.value = if (conference.subject.isNullOrEmpty()) {
            if (conference.me.isFocus) {
                AppUtils.getString(R.string.conference_local_title)
            } else {
                AppUtils.getString(R.string.conference_default_title)
            }
        } else {
            conference.subject
        }

        updateConferenceLayout(conference)
    }

    private fun updateConferenceLayout(conference: Conference) {
        val layout = conference.layout
        conferenceMosaicDisplayMode.value = layout == ConferenceLayout.Grid || layout == ConferenceLayout.None
        conferenceActiveSpeakerDisplayMode.value = layout == ConferenceLayout.ActiveSpeaker
        Log.i("[Conference] Conference current layout is: $layout")
    }

    private fun terminateConference(conference: Conference) {
        conferenceExists.value = false
        isVideoConference.value = false

        conference.removeListener(conferenceListener)

        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        conferenceParticipants.value = arrayListOf()
        conferenceParticipantDevices.value = arrayListOf()
    }

    private fun updateParticipantsList(conference: Conference) {
        conferenceParticipants.value.orEmpty().forEach(ConferenceParticipantData::destroy)
        val participants = arrayListOf<ConferenceParticipantData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            val participantData = ConferenceParticipantData(conference, participant)
            participants.add(participantData)
        }

        conferenceParticipants.value = participants
    }

    private fun updateParticipantsDevicesList(conference: Conference) {
        conferenceParticipantDevices.value.orEmpty().forEach(ConferenceParticipantDeviceData::destroy)
        val devices = arrayListOf<ConferenceParticipantDeviceData>()

        val participantsList = conference.participantList
        Log.i("[Conference] Conference has ${participantsList.size} participants")
        for (participant in participantsList) {
            val participantDevices = participant.devices
            Log.i("[Conference] Participant found: ${participant.address.asStringUriOnly()} with ${participantDevices.size} device(s)")

            for (device in participantDevices) {
                Log.i("[Conference] Participant device found: ${device.name} (${device.address.asStringUriOnly()})")
                val deviceData = ConferenceParticipantDeviceData(device, false)
                devices.add(deviceData)
            }
        }
        for (device in conference.me.devices) {
            Log.i("[Conference] Participant device for myself found: ${device.name} (${device.address.asStringUriOnly()})")
            val deviceData = ConferenceParticipantDeviceData(device, true)
            devices.add(deviceData)
        }

        flexboxLayoutDirection.value = if (devices.size > 3) {
            FlexDirection.ROW
        } else {
            FlexDirection.COLUMN
        }

        conferenceParticipantDevices.value = devices
    }
}
