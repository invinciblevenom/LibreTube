package com.github.libretube.ui.sheets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.github.libretube.R
import com.github.libretube.api.SubscriptionHelper
import com.github.libretube.api.obj.Subscription
import com.github.libretube.databinding.DialogEditChannelGroupBinding
import com.github.libretube.db.DatabaseHolder
import com.github.libretube.db.obj.SubscriptionGroup
import com.github.libretube.ui.adapters.SubscriptionGroupChannelsAdapter
import com.github.libretube.ui.models.SubscriptionsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class EditChannelGroupSheet(
    private var group: SubscriptionGroup,
    private val onGroupChanged: (SubscriptionGroup) -> Unit
) : ExpandedBottomSheet() {
    private var _binding: DialogEditChannelGroupBinding? = null
    private val binding get() = _binding!!

    private val subscriptionsModel: SubscriptionsViewModel by activityViewModels()
    private var channels = listOf<Subscription>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditChannelGroupBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = binding

        binding.groupName.setText(group.name)

        binding.channelsRV.layoutManager = LinearLayoutManager(context)
        fetchSubscriptions()

        binding.groupName.addTextChangedListener {
            updateConfirmStatus()
        }

        binding.searchInput.addTextChangedListener {
            showChannels(channels, it?.toString())
        }

        binding.cancel.setOnClickListener {
            dismiss()
        }

        updateConfirmStatus()
        binding.confirm.setOnClickListener {
            group.name = binding.groupName.text.toString()
            if (group.name.isBlank()) return@setOnClickListener
            onGroupChanged(group)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun fetchSubscriptions() {
        subscriptionsModel.subscriptions.value?.let {
            channels = it
            showChannels(it, null)
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            channels = runCatching {
                SubscriptionHelper.getSubscriptions()
            }.getOrNull().orEmpty()
            withContext(Dispatchers.Main) {
                showChannels(channels, null)
            }
        }
    }

    private fun showChannels(channels: List<Subscription>, query: String?) {
        val binding = binding
        binding.channelsRV.adapter = SubscriptionGroupChannelsAdapter(
            channels.filter { query == null || it.name.lowercase().contains(query.lowercase()) },
            group
        ) {
            group = it
            updateConfirmStatus()
        }
        binding.subscriptionsContainer.isVisible = true
        binding.progress.isVisible = false
    }

    private fun updateConfirmStatus() {
        with(binding) {
            val name = groupName.text.toString()
            groupName.error = getGroupNameError(name)

            confirm.isEnabled = groupName.error == null && group.channels.isNotEmpty()
        }
    }

    private fun getGroupNameError(name: String): String? {
        if (name.isBlank()) {
            return getString(R.string.group_name_error_empty)
        }

        val groupExists = runBlocking(Dispatchers.IO) {
            DatabaseHolder.Database.subscriptionGroupsDao().exists(name)
        }
        if (groupExists && group.name != name) {
            return getString(R.string.group_name_error_exists)
        }

        return null
    }
}
