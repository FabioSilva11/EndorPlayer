package pay.solutions.endorplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
        val checkboxFilterUserId: MaterialCheckBox = findViewById(R.id.checkbox_filter_userid)
        val editEstablishment: TextInputEditText = findViewById(R.id.edit_establishment)
        val layoutEstablishment: TextInputLayout = findViewById(R.id.layout_establishment)
        val editCep: TextInputEditText = findViewById(R.id.edit_cep)
        val layoutCep: TextInputLayout = findViewById(R.id.layout_cep)
        val editRua: TextInputEditText = findViewById(R.id.edit_rua)
        val layoutRua: TextInputLayout = findViewById(R.id.layout_rua)
        val editNumero: TextInputEditText = findViewById(R.id.edit_numero)
        val layoutNumero: TextInputLayout = findViewById(R.id.layout_numero)
        val editUserId: TextInputEditText = findViewById(R.id.edit_user_id)
        val layoutUserId: TextInputLayout = findViewById(R.id.layout_user_id)
        val buttonSave: MaterialButton = findViewById(R.id.button_save)
        val progressSaving: CircularProgressIndicator = findViewById(R.id.progress_saving)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isLandscape = prefs.getInt(KEY_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val establishment = prefs.getString(KEY_ESTABLISHMENT, "") ?: ""
        val cep = prefs.getString(KEY_CEP, "") ?: ""
        val filterEnabled = prefs.getBoolean(KEY_FILTER_ENABLED, false)
        val filterUserId = prefs.getString(KEY_FILTER_VALUE, "") ?: ""

        checkboxLandscape.isChecked = isLandscape
        checkboxPortrait.isChecked = !isLandscape
        editEstablishment.setText(establishment)
        editCep.setText(cep)
        checkboxFilterUserId.isChecked = filterEnabled
        editUserId.setText(filterUserId)
        editUserId.isEnabled = filterEnabled

        checkboxLandscape.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxPortrait.isChecked = false
            else if (!checkboxPortrait.isChecked) checkboxPortrait.isChecked = true
        }
        checkboxPortrait.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) checkboxLandscape.isChecked = false
            else if (!checkboxLandscape.isChecked) checkboxLandscape.isChecked = true
        }
        checkboxFilterUserId.setOnCheckedChangeListener { _, isChecked ->
            editUserId.isEnabled = isChecked
        }

        buttonSave.setOnClickListener {
            val chosenOrientation = if (checkboxLandscape.isChecked) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            val chosenOrientationStr = if (checkboxLandscape.isChecked) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
            val nameValue = editEstablishment.text?.toString()?.trim() ?: ""
            val cepValue = editCep.text?.toString()?.trim() ?: ""
            val ruaValue = editRua.text?.toString()?.trim() ?: ""
            val numeroValue = editNumero.text?.toString()?.trim() ?: ""
            val filterEnabledNow = checkboxFilterUserId.isChecked
            val filterUserIdNow = editUserId.text?.toString()?.trim() ?: ""

            var hasError = false

            if (nameValue.isEmpty()) {
                layoutEstablishment.error = "Informe o nome do estabelecimento"
                hasError = true
            } else {
                layoutEstablishment.error = null
            }

            val cepDigits = cepValue.replace("-", "")
            if (cepValue.isEmpty()) {
                layoutCep.error = "Informe o CEP"
                hasError = true
            } else {
                val cepValid = cepDigits.matches(Regex("\\d{8}"))
                if (!cepValid) {
                    layoutCep.error = "CEP inválido (use 00000-000)"
                    hasError = true
                } else {
                    layoutCep.error = null
                }
            }

            if (filterEnabledNow && filterUserIdNow.isEmpty()) {
                layoutUserId.error = "Informe o filter (número)"
                hasError = true
            } else {
                layoutUserId.error = null
            }

            if (ruaValue.isEmpty()) {
                layoutRua.error = "Informe a rua"
                hasError = true
            } else {
                layoutRua.error = null
            }

            if (numeroValue.isEmpty()) {
                layoutNumero.error = "Informe o número"
                hasError = true
            } else {
                layoutNumero.error = null
            }

            if (hasError) return@setOnClickListener

            // Oculta o botão e mostra o loading
            buttonSave.visibility = View.GONE
            progressSaving.visibility = View.VISIBLE

            prefs.edit()
                .putInt(KEY_ORIENTATION, chosenOrientation)
                .putString(KEY_VIDEO_ORIENTATION, chosenOrientationStr)
                .putString(KEY_ESTABLISHMENT, nameValue)
                .putString(KEY_CEP, if (cepValue.contains("-")) cepValue else cepDigits.substring(0,5) + "-" + cepDigits.substring(5))
                .putBoolean(KEY_SETUP_DONE, true)
                .putBoolean(KEY_FILTER_ENABLED, filterEnabledNow)
                .putString(KEY_FILTER_VALUE, filterUserIdNow)
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
                        startActivity(Intent(this@SettingsActivity, MainActivity::class.java))
                        finish()
                    } else {
                        // Erro ao salvar: mostra o botão novamente
                        buttonSave.visibility = View.VISIBLE
                        progressSaving.visibility = View.GONE
                    }
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
                        if (error != null || !committed) {
                            // Erro na transação: mostra o botão novamente
                            buttonSave.visibility = View.VISIBLE
                            progressSaving.visibility = View.GONE
                            return
                        }
                        val assignedId = (currentData?.getValue(Int::class.java) ?: 11) - 1
                        proceedAfterAuthAndSave(assignedId)
                    }
                })
            }

            if (auth.currentUser == null) {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        ensureIdAndSave()
                    } else {
                        // Erro no login: mostra o botão novamente
                        buttonSave.visibility = View.VISIBLE
                        progressSaving.visibility = View.GONE
                    }
                }
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
        const val KEY_FILTER_ENABLED = "filter_enabled"
        const val KEY_FILTER_VALUE = "filter_value"
    }
}


