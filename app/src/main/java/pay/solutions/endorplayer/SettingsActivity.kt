package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val checkboxLandscape: MaterialCheckBox = findViewById(R.id.checkbox_landscape)
        val checkboxPortrait: MaterialCheckBox = findViewById(R.id.checkbox_portrait)
        val editEstablishment: TextInputEditText = findViewById(R.id.edit_establishment)
        val editCep: TextInputEditText = findViewById(R.id.edit_cep)
        val editRua: TextInputEditText = findViewById(R.id.edit_rua)
        val editNumero: TextInputEditText = findViewById(R.id.edit_numero)
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
            val ruaValue = editRua.text?.toString()?.trim() ?: ""
            val numeroValue = editNumero.text?.toString()?.trim() ?: ""

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

            val auth = FirebaseAuth.getInstance()
            val db = FirebaseDatabase.getInstance().reference

            val proceedAfterAuthAndSave: (Int) -> Unit = { tvId ->
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                val nowIso = isoFormat.format(Date())

                val tvData = mapOf(
                    "id_tv" to tvId,
                    "nome_estabelecimento" to nameValue,
                    "cep" to (if (cepValue.contains("-")) cepValue else cepDigits.substring(0,5) + "-" + cepDigits.substring(5)),
                    "endereco" to mapOf(
                        "rua" to ruaValue,
                        "numero" to (numeroValue.toIntOrNull() ?: 0)
                    ),
                    "orientacao" to if (checkboxLandscape.isChecked) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT,
                    "status_online" to true,
                    "ultimo_visto" to nowIso
                )

                db.child("tvs").child(tvId.toString()).setValue(tvData).addOnCompleteListener { writeTask ->
                    if (writeTask.isSuccessful) {
                        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .putInt(KEY_TV_ID, tvId)
                            .apply()
                    }
                    startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                    finish()
                }
            }

            val ensureIdAndSave: () -> Unit = {
                val counterRef = db.child("counters").child("next_tv_id")
                counterRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(currentData: MutableData): Transaction.Result {
                        val current = (currentData.getValue(Int::class.java) ?: 10)
                        val next = if (current < 10) 10 else current + 1
                        currentData.value = next
                        return Transaction.success(currentData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                        val assignedId = (currentData?.getValue(Int::class.java) ?: 11) - 1
                        proceedAfterAuthAndSave(assignedId)
                    }
                })
            }

            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { ensureIdAndSave() }
            } else {
                ensureIdAndSave()
            }
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
        const val KEY_TV_ID = "tv_id"
    }
}


