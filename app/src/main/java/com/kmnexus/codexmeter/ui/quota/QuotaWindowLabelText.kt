package com.kmnexus.codexmeter.ui.quota

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kmnexus.codexmeter.R

/** The window label, followed by the provider's own name when it gave one ("7 días · Fable"). */
@Composable
fun quotaWindowLabelText(@StringRes labelResId: Int, labelName: String?): String =
    if (labelName == null) {
        stringResource(labelResId)
    } else {
        stringResource(R.string.window_label_named_format, stringResource(labelResId), labelName)
    }

/** [quotaWindowLabelText] for code outside Compose UI (the widget). */
fun Context.quotaWindowLabelString(@StringRes labelResId: Int, labelName: String?): String =
    if (labelName == null) {
        getString(labelResId)
    } else {
        getString(R.string.window_label_named_format, getString(labelResId), labelName)
    }
