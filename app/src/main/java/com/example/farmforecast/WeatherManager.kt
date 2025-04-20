package com.example.farmforecast
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class WeatherManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private val weatherApiKey = "e61605e0572542d94366fe4290a5e649" // Replace with your API key
    private val weatherApiUrl = "https://api.openweathermap.org/data/2.5/weather"

    private val weatherIcons = mapOf(
        "Clear" to R.drawable.ic_weather_sunny,
        "Clouds" to R.drawable.ic_weather_cloudy,
        "Rain" to R.drawable.ic_weather_rainy,
        "Snow" to R.drawable.ic_weather_snowy,
        "Thunderstorm" to R.drawable.ic_weather_thunderstorm,
        "Drizzle" to R.drawable.ic_weather_rainy,
        "Mist" to R.drawable.ic_weather_foggy,
        "Fog" to R.drawable.ic_weather_foggy
    )

    fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun updateWeatherInToolbar(textView: TextView) {
        if (!hasLocationPermission()) {
            textView.text = "No location permission"
            return
        }

        try {
            val location = getCurrentLocation()
            if (location != null) {
                val weatherData = fetchWeatherData(location.latitude, location.longitude)
                updateWeatherUI(textView, weatherData)
            } else {
                textView.text = "Location unavailable"
            }
        } catch (e: Exception) {
            textView.text = "Weather unavailable"
            e.printStackTrace()
        }
    }

    private suspend fun getCurrentLocation(): Location? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission()) {
            return@withContext null
        }

        try {
            val cancellationToken = CancellationTokenSource()
            return@withContext fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private suspend fun fetchWeatherData(lat: Double, lon: Double): JSONObject = withContext(Dispatchers.IO) {
        val url = "$weatherApiUrl?lat=$lat&lon=$lon&units=metric&appid=$weatherApiKey"
        val response = URL(url).readText()
        return@withContext JSONObject(response)
    }

    private fun updateWeatherUI(textView: TextView, weatherData: JSONObject) {
        val temp = weatherData.getJSONObject("main").getDouble("temp").toInt()
        val condition = weatherData.getJSONArray("weather").getJSONObject(0).getString("main")

        // Set temperature text
        textView.text = "$temp°C"

        // Set weather icon
        val icon = weatherIcons[condition] ?: R.drawable.ic_weather_sunny
        val drawable = AppCompatResources.getDrawable(context, icon)
        textView.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
    }

    // Extension function to await Task result
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return withContext(Dispatchers.IO) {
            this@await.addOnCanceledListener {
                throw Exception("Task was cancelled")
            }.addOnFailureListener {
                throw it
            }

            var result: T? = null
            while (!this@await.isComplete) {
                Thread.sleep(10)
            }
            result = this@await.result
            return@withContext result!!
        }
    }
}