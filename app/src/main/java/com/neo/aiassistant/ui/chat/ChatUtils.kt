package com.neo.aiassistant.ui.chat

import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned

fun Modifier.positionAwareImePadding() = composed {
    this.onGloballyPositioned { _ -> }.imePadding()
}
