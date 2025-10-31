package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val checkboxLandscape: MaterialCheckBox = findViewById(R.id.checkbox_landscape)
        val checkboxPortrait: MaterialCheckBox = findViewById(R.id.checkbox_portrait)
        val editEstablishment: TextInputEditText = findViewById(R.id.edit_establishment)
        val editCep: TextInputEditText = findViewById(R.id.edit_cep)
        val buttonSave: MaterialButton = findViewById(R.id.button_save)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLandscape = prefs.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val establishment = prefs.getString(KEY_ESTABLISHMENT, "") ?: ""
        val cep = prefs.getString(KEY_CEP, "") ?: ""

        checkboxLandscape.isChecked = isLandscape
        checkboxPortrait.isChecked = !isLandscape
        editEstablishment.setText(establishment)
        editCep.setText(cep)

        checkboxLandscape.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxPortrait.isChecked = false
            else if (!checkboxPortrait.isChecked) checkboxPortrait.isChecked = true
        }
        checkboxPortrait.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxLandscape.isChecked = false
            else if (!checkboxLandscape.isChecked) checkboxLandscape.isChecked = true
        }

        buttonSave.setOnClickListener {
            val chosenOrientation = if (checkboxLandscape.isChecked) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val chosenOrientationStr = if (checkboxLandscape.isChecked) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
            val nameValue = editEstablishment.text?.toString()?.trim() ?: ""
            val cepValue = editCep.text?.toString()?.trim() ?: ""

            var hasError = false

            if (nameValue.isEmpty()) {
                editEstablishment.error = "Informe o nome do estabelecimento"
                hasError = true
            } else {
                editEstablishment.error = null
            }

            val cepDigits = cepValue.replace("-", "")
            val cepValid = cepDigits.matches(Regex("\\d{8}"))
            if (!cepValid) {
                editCep.error = "CEP inválido (use 00000-000)"
                hasError = true
            } else {
                editCep.error = null
            }

            if (hasError) return@setOnClickListener

            prefs.edit()
                .putInt(KEY_ORIENTATION, chosenOrientation)
                .putString(KEY_VIDEO_ORIENTATION, chosenOrientationStr)
                .putString(KEY_ESTABLISHMENT, nameValue)
                .putString(KEY_CEP, if (cepValue.contains("-")) cepValue else cepDigits.substring(0,5) + "-" + cepDigits.substring(5))
                .putBoolean(KEY_SETUP_DONE, true)
                .apply()

            startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        const val PREFS_NAME = "endor_prefs"
        const val KEY_ORIENTATION = "orientation"
        const val KEY_VIDEO_ORIENTATION = "video_orientation"
        const val ORIENTATION_LANDSCAPE = "landscape"
        const val ORIENTATION_PORTRAIT = "portrait"
        const val KEY_ESTABLISHMENT = "establishment_name"
        const val KEY_CEP = "cep"
        const val KEY_SETUP_DONE = "setup_done"
    }
}


