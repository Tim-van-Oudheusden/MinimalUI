package dev.buildwithtim.minimalui.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import dev.buildwithtim.minimalui.R
import dev.buildwithtim.minimalui.core.Choice
import dev.buildwithtim.minimalui.core.Setting
import dev.buildwithtim.minimalui.core.Settings
import dev.buildwithtim.minimalui.core.SettingsLink
import dev.buildwithtim.minimalui.core.SettingsRow
import dev.buildwithtim.minimalui.core.StatusPill
import dev.buildwithtim.minimalui.core.settingsRows

/**
 * MinimalUI's own settings (#73), a list in the app list's style that takes its place on screen, under the [title]
 * pill. A setting's row shows its value from [settings] at the right end. Left and Right cycle the selected setting's
 * value back and forth ([onCycle] with -1 or 1), Enter or a tap moves it forward; on a link row they open its list
 * ([onOpen]). Back or Esc calls [onClose]. The selection lives in [state], so the list comes back from a link's list
 * on that row.
 */
@Composable
internal fun SettingsList(
    title: String,
    settings: Settings,
    onCycle: (Setting, Int) -> Unit,
    onOpen: (SettingsLink) -> Unit,
    onClose: () -> Unit,
    state: PillListState,
    modifier: Modifier = Modifier,
    status: StatusPill? = null,
) {
    val settingLabels = Setting.entries.associateWith { stringResource(it.label) }
    val linkLabels = SettingsLink.entries.associateWith { stringResource(it.label) }
    val choiceLabels = Choice.entries.associateWith { stringResource(it.label) }

    TitledList(title, status, onClose, modifier) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = settingsRows,
            // Lazy-list keys must fit in a Bundle; the row's text form is unique.
            key = { it.toString() },
            label = { row ->
                when (row) {
                    is SettingsRow.Value -> settingLabels.getValue(row.setting)
                    is SettingsRow.Link -> linkLabels.getValue(row.link)
                }
            },
            value = { row -> (row as? SettingsRow.Value)?.let { choiceLabels.getValue(settings[it.setting]) } },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = { row ->
                when (row) {
                    is SettingsRow.Value -> onCycle(row.setting, 1)
                    is SettingsRow.Link -> onOpen(row.link)
                }
            },
            // Left and Right never page here: the list is short, and on a link row they would only move the pill.
            onKey = { key, row ->
                when (key) {
                    Key.Escape -> {
                        onClose()
                        true
                    }
                    Key.DirectionLeft, Key.DirectionRight -> {
                        if (row is SettingsRow.Value) onCycle(row.setting, if (key == Key.DirectionLeft) -1 else 1)
                        true
                    }
                    else -> false
                }
            },
        )
    }
}

/** The About list (#73): the app's [version] and the [commit] it was built from, one per row. Back or Esc closes it. */
@Composable
internal fun AboutList(
    version: String,
    commit: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    status: StatusPill? = null,
) {
    val state = remember { PillListState() }
    val rows = listOf(stringResource(R.string.about_version, version), stringResource(R.string.about_commit, commit))

    TitledList(stringResource(R.string.settings_about), status, onClose, modifier) { rowHeight, rowsPerPage ->
        PillList(
            state = state,
            items = rows,
            key = { it },
            label = { it },
            rowHeight = rowHeight,
            rowsPerPage = rowsPerPage,
            onConfirm = {},
            onKey = { key, _ -> (key == Key.Escape).also { if (it) onClose() } },
        )
    }
}

private val Setting.label: Int
    get() = when (this) {
        Setting.Clock -> R.string.setting_clock
        Setting.BatteryPercentage -> R.string.setting_battery_percentage
        Setting.RecentlyUsed -> R.string.setting_recently_used
    }

private val SettingsLink.label: Int
    get() = when (this) {
        SettingsLink.HiddenApps -> R.string.settings_hidden_apps
        SettingsLink.About -> R.string.settings_about
    }

private val Choice.label: Int
    get() = when (this) {
        Choice.On -> R.string.choice_on
        Choice.Off -> R.string.choice_off
        Choice.Three -> R.string.choice_three
        Choice.Five -> R.string.choice_five
        Choice.Fifteen -> R.string.choice_fifteen
    }
