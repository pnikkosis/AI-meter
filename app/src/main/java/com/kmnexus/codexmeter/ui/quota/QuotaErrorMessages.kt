package com.kmnexus.codexmeter.ui.quota

import androidx.annotation.StringRes
import com.kmnexus.codexmeter.R

/** User-facing message for a `QuotaError.safeMessageKey`; each error kind keeps its own text. */
@StringRes
fun quotaErrorMessageRes(safeMessageKey: String?): Int = when (safeMessageKey) {
    "error_auth_required" -> R.string.error_auth_required
    "error_network" -> R.string.error_network
    "error_rate_limited" -> R.string.error_rate_limited
    "error_provider_unavailable" -> R.string.error_provider_unavailable
    "error_response_invalid" -> R.string.error_response_invalid
    else -> R.string.error_unknown
}
