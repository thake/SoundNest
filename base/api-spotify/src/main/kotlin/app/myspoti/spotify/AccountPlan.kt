package app.myspoti.spotify

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * The account's plan, from the Web Player's accountAttributes answer (`data.me.account`: `product`
 * and `attributes.ads`). Read once the page is signed in, and forgotten when the account signs out,
 * so it always says which account is active now.
 *
 * [premium] is null until it is read, true for a paid ad-free plan (Premium, Duo, Family, Student),
 * and false for a free or ad-supported account (which plays audio and ads via the Web Player host).
 */
object AccountPlan {
    private val _premium = MutableStateFlow<Boolean?>(null)
    val premium: StateFlow<Boolean?> = _premium

    /** True when the account is on an ad-free paid plan. */
    val isPremium: Boolean get() = _premium.value == true

    /** The plan read, or forgotten (null): a sign-out, or a new sign-in. */
    fun set(premium: Boolean?) { _premium.value = premium }

    /** The plan in an accountAttributes answer, `{"data":{"me":{"account":...}}}`; null when it does not say. */
    fun fromAnswer(answer: JsonElement?): Boolean? {
        val me = ((answer as? JsonObject)?.get("data") as? JsonObject)?.get("me") as? JsonObject ?: return null
        val account = me["account"] as? JsonObject ?: return null
        return fromAccount(account)
    }

    /**
     * The plan of an account object: a product other than FREE or OPEN (PREMIUM, DUO, FAMILY,
     * STUDENT...) with `attributes.ads` false is Premium; FREE or OPEN is free, whatever the ads say.
     * Null when the product or the ads flag is missing (a paid product with unknown ads is not guessed).
     */
    fun fromAccount(account: JsonObject): Boolean? {
        val product = (account["product"] as? JsonPrimitive)?.contentOrNull?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (product == "free" || product == "open") return false
        val ads = ((account["attributes"] as? JsonObject)?.get("ads") as? JsonPrimitive)?.booleanOrNull ?: return null
        return !ads
    }
}
