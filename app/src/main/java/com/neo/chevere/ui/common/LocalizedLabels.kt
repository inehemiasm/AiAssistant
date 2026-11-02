package com.neo.chevere.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.neo.chevere.R
import com.neo.chevere.domain.InstallStatus

/** Localized display labels; persisted and protocol enum names remain unchanged. */
@Composable
internal fun InstallStatus.localizedLabel(): String = stringResource(when (this) {
    InstallStatus.INSTALLED -> R.string.ui_installed
    InstallStatus.DOWNLOADING -> R.string.ui_downloading
    InstallStatus.VERIFYING -> R.string.ui_verifying
    InstallStatus.FAILED -> R.string.ui_failed
    InstallStatus.INVALID -> R.string.ui_invalid
    InstallStatus.CORRUPTED -> R.string.ui_corrupted
    InstallStatus.UNSUPPORTED -> R.string.ui_unsupported
    InstallStatus.PENDING_DELETE -> R.string.ui_pending_deletion
})
