package com.pengnini.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier

internal fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
