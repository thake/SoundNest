/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.spokenword.audible.settings

import android.content.Context
import androidx.annotation.StringRes
import io.github.thake.soundnest.R
import io.github.thake.soundnest.audible.AudibleMarketplace

@get:StringRes
val AudibleMarketplace.labelRes: Int
    get() = when (this) {
        AudibleMarketplace.US -> R.string.audible_marketplace_us
        AudibleMarketplace.DE -> R.string.audible_marketplace_de
        AudibleMarketplace.UK -> R.string.audible_marketplace_uk
        AudibleMarketplace.FR -> R.string.audible_marketplace_fr
        AudibleMarketplace.CA -> R.string.audible_marketplace_ca
        AudibleMarketplace.AU -> R.string.audible_marketplace_au
        AudibleMarketplace.IT -> R.string.audible_marketplace_it
        AudibleMarketplace.ES -> R.string.audible_marketplace_es
        AudibleMarketplace.JP -> R.string.audible_marketplace_jp
        AudibleMarketplace.IN -> R.string.audible_marketplace_in
        AudibleMarketplace.BR -> R.string.audible_marketplace_br
    }

fun AudibleMarketplace.localizedName(context: Context): String =
    context.getString(labelRes)
