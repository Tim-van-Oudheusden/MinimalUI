package dev.buildwithtim.minimalui.core

/** A value a setting can take. Each [Setting] lists the ones it offers; later settings add their own kinds. */
enum class Choice { On, Off }

/**
 * A user-facing setting of the launcher (#73), stored under [key]: one of its [choices], [default] until the user
 * changes it. The settings list shows them in this order. Adding a setting is one entry here, plus its label in the
 * app.
 */
enum class Setting(val key: String, val choices: List<Choice>, val default: Choice) {
    /** The clock in the status pill. */
    Clock("status_clock", listOf(Choice.On, Choice.Off), Choice.On),

    /** The percentage next to the battery glyph in the status pill; the glyph itself stays. */
    BatteryPercentage("status_battery_percentage", listOf(Choice.On, Choice.Off), Choice.On),
}

/**
 * A row of the settings list that opens a list of its own instead of holding a value. Adding one is one entry here,
 * plus its label and its list in the app. About stays last.
 */
enum class SettingsLink {
    /** The app's version and the commit it was built from. */
    About,
}

/** One row of the settings list: a setting and its value, or a link to another list. */
sealed interface SettingsRow {
    data class Value(val setting: Setting) : SettingsRow

    data class Link(val link: SettingsLink) : SettingsRow
}

/** The settings list, top to bottom: every setting, then the link rows, About last. */
val settingsRows: List<SettingsRow> =
    Setting.entries.map { SettingsRow.Value(it) } + SettingsLink.entries.map { SettingsRow.Link(it) }

/** What the user chose for each [Setting]; one never changed has its default. */
data class Settings(private val chosen: Map<Setting, Choice> = Setting.entries.associateWith { it.default }) {

    operator fun get(setting: Setting): Choice = chosen[setting] ?: setting.default

    fun isOn(setting: Setting): Boolean = this[setting] == Choice.On

    /**
     * These settings with [setting] moved [step] choices along its list (1 forward, -1 back). It wraps around at both
     * ends rather than stopping, so Left and Right both flip an On/Off setting, and any setting's every value can be
     * reached in either direction.
     */
    fun cycled(setting: Setting, step: Int): Settings {
        val choices = setting.choices
        val next = choices[Math.floorMod(choices.indexOf(this[setting]) + step, choices.size)]
        return Settings(chosen + (setting to next))
    }

    /** Each setting's key with the name of its choice, for app-private storage. */
    fun stored(): Map<String, String> = Setting.entries.associate { it.key to this[it].name }

    companion object {
        /**
         * The settings [stored] before, read key by key with [read]. A missing value, or one the setting does not
         * offer (any more), leaves that setting at its default.
         */
        fun fromStored(read: (String) -> String?): Settings = Settings(
            Setting.entries.associateWith { setting ->
                val name = read(setting.key)
                setting.choices.firstOrNull { it.name == name } ?: setting.default
            },
        )
    }
}
