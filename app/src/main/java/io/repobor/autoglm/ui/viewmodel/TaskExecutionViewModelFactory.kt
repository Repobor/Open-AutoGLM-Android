package io.repobor.autoglm.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.repobor.autoglm.data.SettingsRepository

/**
 * Factory for creating TaskExecutionViewModel with SettingsRepository dependency injection.
 */
class TaskExecutionViewModelFactory(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskExecutionViewModel::class.java)) {
            return TaskExecutionViewModel(context, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
