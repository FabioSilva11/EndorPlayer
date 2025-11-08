package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val checkboxLandscape: MaterialCheckBox = findViewById(R.id.checkbox_landscape)
        val checkboxPortrait: MaterialCheckBox = findViewById(R.id.checkbox_portrait)
        val checkboxFilter: MaterialCheckBox = findViewById(R.id.checkbox_filter)
        val editFilter: TextInputEditText = findViewById(R.id.edit_filter_code)
        val layoutFilter: TextInputLayout = findViewById(R.id.layout_filter_code)
        val buttonSave: MaterialButton = findViewById(R.id.button_save)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val orientationStr = prefs.getString(KEY_VIDEO_ORIENTATION, ORIENTATION_LANDSCAPE) ?: ORIENTATION_LANDSCAPE
        val isLandscape = orientationStr == ORIENTATION_LANDSCAPE
        val filterEnabled = prefs.getBoolean(KEY_FILTER_ENABLED, false)
        val filterCode = prefs.getString(KEY_FILTER_VALUE, "") ?: ""

        checkboxLandscape.isChecked = isLandscape
        checkboxPortrait.isChecked = !isLandscape
        checkboxFilter.isChecked = filterEnabled
        editFilter.setText(filterCode)
        editFilter.isEnabled = filterEnabled

        checkboxLandscape.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxPortrait.isChecked = false else checkboxPortrait.isChecked = true
        }
        checkboxPortrait.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxLandscape.isChecked = false else checkboxLandscape.isChecked = true
        }
        checkboxFilter.setOnCheckedChangeListener { _, isChecked ->
            editFilter.isEnabled = isChecked
        }

        buttonSave.setOnClickListener {
            val chosenOrientationStr = if (checkboxLandscape.isChecked) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
            val filterEnabledNow = checkboxFilter.isChecked
            val filterCodeNow = editFilter.text?.toString()?.trim() ?: ""

            var hasError = false
            if (filterEnabledNow && filterCodeNow.isEmpty()) {
                layoutFilter.error = "Informe o código do filtro (número)"
                hasError = true
            } else {
                layoutFilter.error = null
            }
            if (hasError) return@setOnClickListener

            prefs.edit()
                .putString(KEY_VIDEO_ORIENTATION, chosenOrientationStr)
                .putBoolean(KEY_FILTER_ENABLED, filterEnabledNow)
                .putString(KEY_FILTER_VALUE, filterCodeNow)
                .putBoolean(KEY_SETUP_COMPLETED, true)
                .apply()
            startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        const val PREFS_NAME = "endor_prefs"
        const val KEY_VIDEO_ORIENTATION = "video_orientation"
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val KEY_FILTER_ENABLED = "filter_enabled"
        const val KEY_FILTER_VALUE = "filter_value"
        const val KEY_SETUP_COMPLETED = "setup_completed"
    }
}


