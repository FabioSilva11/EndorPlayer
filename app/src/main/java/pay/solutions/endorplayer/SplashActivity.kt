package pay.solutions.endorplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val versionTextView: TextView = findViewById(R.id.text_version)
        val versionName: String = try {
            val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        versionTextView.text = "Versão $versionName"

        Handler(Looper.getMainLooper()).postDelayed({
            val auth = FirebaseAuth.getInstance()
            val isLoggedIn = auth.currentUser != null
            val next = if (isLoggedIn) MainActivity::class.java else SettingsActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }, 2500)
    }
}


