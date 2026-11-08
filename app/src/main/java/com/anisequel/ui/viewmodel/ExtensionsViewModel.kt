package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.extensions.CatalogExtension
import com.anisequel.data.extensions.CatalogFailure
import com.anisequel.data.extensions.ExtensionCatalog
import com.anisequel.data.extensions.ExtensionManager
import com.anisequel.data.extensions.ExtensionRepository
import com.anisequel.data.extensions.InstallOutcome
import com.anisequel.data.extensions.InstalledExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the Extensions page reads, in one snapshot. */
data class ExtensionsUiState(
    val repositories: List<ExtensionRepository> = emptyList(),
    val catalog: ExtensionCatalog = ExtensionCatalog(),
    val installed: Map<String, InstalledExtension> = emptyMap(),
    val restartRequired: Boolean = false,
    val showNsfw: Boolean = false,
    val isLoading: Boolean = true,
    val isAddingRepository: Boolean = false,
    val installingKey: String? = null,
    val installProgress: Float = 0f,
    val message: String? = null
) {
    val failures: List<CatalogFailure> get() = catalog.errors
    val entries: List<CatalogExtension> get() = catalog.entries
}

/**
 * The Extensions page's state and writes.
 *
 * It owns no repository logic: [ExtensionManager] is the process-wide owner of
 * the catalogue and the install records, and this class only mirrors it for
 * Compose and serialises the one install at a time the UI can show progress for.
 * Two concurrent downloads would share one progress bar and one error line, which
 * is worse than making the second tap wait.
 */
class ExtensionsViewModel(
    private val manager: ExtensionManager
) : ViewModel() {

    private val _state = MutableStateFlow(ExtensionsUiState())
    val state: StateFlow<ExtensionsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            manager.repositories.collect { repositories ->
                _state.update { it.copy(repositories = repositories) }
            }
        }
        viewModelScope.launch {
            manager.installed.collect { installed ->
                _state.update { it.copy(installed = installed) }
            }
        }
        viewModelScope.launch {
            manager.restartRequired.collect { required ->
                _state.update { it.copy(restartRequired = required) }
            }
        }
        viewModelScope.launch {
            manager.showNsfw.collect { show ->
                _state.update { it.copy(showNsfw = show) }
            }
        }
        loadCatalog()
    }

    fun loadCatalog(force: Boolean = false) {
        viewModelScope.launch {
            if (_state.value.catalog.isCold) {
                _state.update { it.copy(isLoading = true) }
            }
            val catalog = manager.loadCatalog(force)
            _state.update { it.copy(catalog = catalog, isLoading = false) }
            if (force && catalog.errors.isNotEmpty()) {
                _state.update {
                    it.copy(
                        message = "${catalog.errors.size} repository read " +
                            if (catalog.errors.size == 1) "failed." else "failed."
                    )
                }
            }
        }
    }

    fun addRepository(url: String) {
        if (url.isBlank() || _state.value.isAddingRepository) return
        viewModelScope.launch {
            _state.update { it.copy(isAddingRepository = true) }
            val result = runCatching { manager.addRepository(url) }
            _state.update { current ->
                result.fold(
                    onSuccess = { discovery ->
                        current.copy(
                            isAddingRepository = false,
                            message = "${discovery.repository.kind.label} repository added \u00b7 " +
                                "${discovery.entries.size} extensions",
                            catalog = ExtensionCatalog()
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isAddingRepository = false,
                            message = error.message ?: "That URL could not be read"
                        )
                    }
                )
            }
            if (result.isSuccess) loadCatalog(force = true)
        }
    }

    fun removeRepository(repository: ExtensionRepository) {
        manager.removeRepository(repository.id)
        _state.update { it.copy(catalog = ExtensionCatalog(), message = "Repository removed.") }
        loadCatalog(force = true)
    }

    fun setShowNsfw(show: Boolean) = manager.preferences.setShowNsfw(show)

    fun install(extension: CatalogExtension) {
        if (_state.value.installingKey != null) return
        viewModelScope.launch {
            _state.update { it.copy(installingKey = extension.key, installProgress = 0f) }
            val outcome = manager.install(extension) { progress ->
                _state.update { it.copy(installProgress = progress) }
            }
            _state.update {
                it.copy(
                    installingKey = null,
                    installProgress = 0f,
                    message = when (outcome) {
                        is InstallOutcome.Installed -> outcome.message
                        is InstallOutcome.NeedsPermission -> outcome.message
                        is InstallOutcome.Failed -> outcome.message
                    }
                )
            }
        }
    }

    fun uninstall(extension: CatalogExtension) {
        val message = manager.uninstall(extension)
        _state.update { it.copy(message = message) }
    }

    fun uninstall(record: InstalledExtension) {
        manager.uninstall(record)
        _state.update {
            it.copy(message = "${record.name} removed. Restart to refresh the extension list.")
        }
    }

    fun restart() {
        if (!manager.restart()) {
            _state.update { it.copy(message = "Could not restart the app from here.") }
        }
    }

    fun openInstallPermissionSettings() = manager.openInstallPermissionSettings()

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    class Factory(private val manager: ExtensionManager) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ExtensionsViewModel(manager) as T
    }
}
