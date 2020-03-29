/*
 * Copyright (c) 2010-2020 Belledonne Communications SARL.
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
package org.linphone.activities.main.chat.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import org.linphone.activities.main.chat.viewmodels.DevicesListViewModel
import org.linphone.activities.main.chat.viewmodels.DevicesListViewModelFactory
import org.linphone.activities.main.viewmodels.SharedMainViewModel
import org.linphone.databinding.ChatRoomDevicesFragmentBinding

class DevicesFragment : Fragment() {
    private lateinit var binding: ChatRoomDevicesFragmentBinding
    private lateinit var listViewModel: DevicesListViewModel
    private lateinit var sharedViewModel: SharedMainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = ChatRoomDevicesFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this

        sharedViewModel = activity?.run {
            ViewModelProvider(this).get(SharedMainViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        val chatRoom = sharedViewModel.selectedChatRoom.value
        chatRoom ?: return

        listViewModel = ViewModelProvider(
            this,
            DevicesListViewModelFactory(chatRoom)
        )[DevicesListViewModel::class.java]
        binding.viewModel = listViewModel

        binding.setBackClickListener {
            findNavController().popBackStack()
        }
    }
}
