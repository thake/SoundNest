package io.github.thake.soundnest.core.model

/** The logged-in Spotify account, shown in the Library settings menu. */
data class AccountModel(
    val name: String = "",
    val email: String = "",
    val imageUrl: String = "",
    val plan: String = "",
)
