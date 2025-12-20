package io.repobor.autoglm.ui.overlay

import io.repobor.autoglm.ui.viewmodel.TaskExecutionViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Global state holder for sharing TaskExecutionViewModel with FloatingWindowService.
 * This allows the service to access the ViewModel without needing Activity context.
 */
object FloatingWindowStateHolder {
    private var currentViewModel: TaskExecutionViewModel? = null

    // Event to request expanding the floating window
    private val _expandFloatingWindow = MutableSharedFlow<Unit>()
    val expandFloatingWindow: SharedFlow<Unit> = _expandFloatingWindow.asSharedFlow()

    fun setViewModel(viewModel: TaskExecutionViewModel?) {
        currentViewModel = viewModel
    }

    fun getViewModel(): TaskExecutionViewModel? = currentViewModel

    fun clearViewModel() {
        currentViewModel = null
    }

    suspend fun requestExpand() {
        _expandFloatingWindow.emit(Unit)
    }
}
