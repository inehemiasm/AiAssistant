package com.neo.chevere.ui.chat.components

import androidx.annotation.StringRes
import com.neo.chevere.R

/** A stable command token with localized presentation labels. */
internal data class SlashCommand(
    val command: String,
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
)

/** User-visible autocomplete belongs to presentation; command tokens remain locale-independent. */
internal val chatSlashCommands = listOf(
    SlashCommand("/voice", R.string.slash_voice_title, R.string.slash_voice_description),
    SlashCommand("/conversation", R.string.hands_free_mode_title, R.string.hands_free_start),
    SlashCommand("/sensors", R.string.slash_sensors_title, R.string.slash_sensors_description),
    SlashCommand("/stud", R.string.slash_stud_title, R.string.slash_stud_description),
    SlashCommand("/level", R.string.slash_level_title, R.string.slash_level_description),
    SlashCommand("/light", R.string.slash_light_title, R.string.slash_light_description),
    SlashCommand("/proximity", R.string.slash_proximity_title, R.string.slash_proximity_description),
)
