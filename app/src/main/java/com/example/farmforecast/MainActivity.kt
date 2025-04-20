package com.example.farmforecast
import android.os.Bundle
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.farmforecast.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : AppCompatActivity()
{

    private lateinit var binding: ActivityMainBinding
    private lateinit var translator: Translator

    private lateinit var weatherManager: WeatherManager
    private lateinit var weatherTextView: TextView



    fun getMlKitLangCode(systemLang: String): String
    {
        return when (systemLang)
        {
            "en" -> TranslateLanguage.ENGLISH
            "hi" -> TranslateLanguage.HINDI
            "te" -> TranslateLanguage.TELUGU
            "ta" -> TranslateLanguage.TAMIL
            "kn" -> TranslateLanguage.KANNADA
            "mr" -> TranslateLanguage.MARATHI
            "gu" -> TranslateLanguage.GUJARATI
            else -> TranslateLanguage.ENGLISH
        }
    }

    fun translateText(text: String, translator: Translator, callback: (String) -> Unit)
    {
        translator.translate(text)
            .addOnSuccessListener { translatedText -> callback(translatedText) }
            .addOnFailureListener { callback(text) }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set up binding first
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentLang = resources.configuration.locales[0].language
        val targetLang = getMlKitLangCode(currentLang)
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(targetLang)
            .build()

        translator = Translation.getClient(options)

        // Set up navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        val navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_market, R.id.navigation_leafdiagnosis, R.id.navigation_crop_recommend)
        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
        binding.navView.setupWithNavController(navController)

        // Set up weather
        weatherManager = WeatherManager(this)

        // Download translation model and translate only specific elements
        translator.downloadModelIfNeeded().addOnSuccessListener {
            // Only translate navigation items for now
            translateBottomNavTitles()
        }.addOnFailureListener { e ->
            Log.e("Translation", "Failed to download language model", e)
        }

        // Request location permissions if not granted
        if (!weatherManager.hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        else {
//            updateWeather()
        }
    }
    // Add this to MainActivity
    fun translateFragmentViews(rootView: View, excludeIds: List<Int> = emptyList()) {
        translateViewsSelectively(rootView, excludeIds)
    }

    private fun translateViewsSelectively(view: View, excludeIds: List<Int>) {
        if (excludeIds.contains(view.id)) {
            return
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                translateViewsSelectively(view.getChildAt(i), excludeIds)
            }
        } else if (view is TextView) {
            val originalText = view.text.toString()
            if (originalText.isNotBlank()) {
                translateText(originalText) { translated ->
                    if (view.isAttachedToWindow) {
                        view.text = translated
                    }
                }

                // Also translate hint if it exists (for EditText)
                if (view is android.widget.EditText && view.hint != null) {
                    val originalHint = view.hint.toString()
                    if (originalHint.isNotBlank()) {
                        translateText(originalHint) { translated ->
                            if (view.isAttachedToWindow) {
                                view.hint = translated
                            }
                        }
                    }
                }
            }
        }
    }


    fun translateBottomNavTitles() {
        val navView = findViewById<BottomNavigationView>(R.id.nav_view)

        val menu = navView.menu
        val titles = listOf("Market Prices", "Leaf Diagnosis", "Crop Advisor")

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            translateText(titles[i]) { translated ->
                item.title = translated
            }
        }
    }

    fun translateText(text: String, callback: (String) -> Unit) {
        if (text.isBlank()) {
            callback(text)
            return
        }

        translator.translate(text)
            .addOnSuccessListener { translatedText -> callback(translatedText) }
            .addOnFailureListener {
                Log.e("Translation", "Failed to translate: $text", it)
                callback(text)
            }
    }
    fun translateAllViews(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                translateAllViews(view.getChildAt(i))
            }
        } else if (view is TextView) {

            // For androidx.appcompat.widget.AppCompatTextView which might be the toolbar title
            if (view.parent is androidx.appcompat.widget.Toolbar &&
                view.text.toString().contains("Farm Forecast")) {
                return // Skip toolbar title
            }

            val originalText = view.text.toString()
            if (originalText.isNotBlank()) {
                translateText(originalText) { translated ->
                    view.text = translated
                }

                // Also translate hint if it exists (for EditText)
                if (view is android.widget.EditText && view.hint != null) {
                    val originalHint = view.hint.toString()
                    if (originalHint.isNotBlank()) {
                        translateText(originalHint) { translated ->
                            view.hint = translated
                        }
                    }
                }
            }
        }
    }
    fun translateStrings(strings: List<String>, callback: (List<String>) -> Unit) {
        if (strings.isEmpty()) {
            callback(strings)
            return
        }

        val translatedStrings = mutableListOf<String>()
        var completedCount = 0

        strings.forEachIndexed { index, text ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    synchronized(translatedStrings) {
                        // Make sure we preserve original order
                        translatedStrings.add(index, translatedText)
                        completedCount++

                        if (completedCount == strings.size) {
                            callback(translatedStrings)
                        }
                    }
                }
                .addOnFailureListener {
                    synchronized(translatedStrings) {
                        translatedStrings.add(index, text)
                        completedCount++

                        if (completedCount == strings.size) {
                            callback(translatedStrings)
                        }
                    }
                }
        }
    }
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                updateWeather()
            } else {
                weatherTextView.text = "Location permission denied"
            }
        }
    }

    override fun onResume() {
        super.onResume()
//        if (weatherManager.hasLocationPermission()) {
//            updateWeather()
//        }
    }

//    private fun updateWeather() {
//        lifecycleScope.launch {
//            weatherManager.updateWeatherInToolbar(weatherTextView)
//        }
//    }
}
