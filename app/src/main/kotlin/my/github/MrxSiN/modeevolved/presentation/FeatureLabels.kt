package my.github.MrxSiN.modeevolved.presentation

import my.github.MrxSiN.modeevolved.R
import my.github.MrxSiN.modeevolved.action.SystemSetting
import my.github.MrxSiN.modeevolved.core.Feature

/** What each hooked feature is called where a person reads the compatibility check. */
object FeatureLabels {

    fun of(feature: Feature): Int = when (feature) {
        Feature.MODE_PAGE -> R.string.feature_mode_page
        Feature.MODES_LIST -> R.string.feature_modes_list
        Feature.EDITOR_RESULTS -> R.string.feature_editor_results
        Feature.BUTTON_GROUP -> R.string.feature_button_group
        Feature.CALENDAR_ROW -> R.string.feature_calendar_row
        Feature.SCHEDULE_REMOVAL -> R.string.feature_schedule_removal
        Feature.SETTINGS_RESTART -> R.string.feature_settings_restart
        Feature.AUTOMATION_START -> R.string.feature_automation_start
        Feature.CALENDAR_FILTER -> R.string.feature_calendar_filter
        Feature.TRIGGER_AUTOMATION -> R.string.feature_trigger_automation
        Feature.ACTION_AUTOMATION -> R.string.feature_action_automation
        else -> SettingTexts.labelOf(SystemSetting.entries.first { it.feature == feature })
    }
}
