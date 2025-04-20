package com.example.farmforecast.ui.suggestion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.farmforecast.MainActivity
import com.example.farmforecast.R
import com.example.farmforecast.databinding.FragmentCommodityPriceBinding
import com.example.farmforecast.databinding.FragmentCropSuggestionBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
class CropSuggestionFragment : Fragment() {
    private var _binding: FragmentCropSuggestionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropSuggestionBinding.inflate(inflater, container, false)
        return binding.root
    }


    fun translateText(text: String, translator: Translator, callback: (String) -> Unit) {
        translator.translate(text)
            .addOnSuccessListener { translatedText -> callback(translatedText) }
            .addOnFailureListener { callback(text) } // fallback to original
    }

    // Get reference to MainActivity for translation functions
    private val mainActivity: MainActivity?
        get() = activity as? MainActivity
    private fun translateEditTextPlaceholders() {
        mainActivity?.let { activity ->
            activity.translateText(binding.tilNitrogen.hint.toString()) { translatedHint ->
                binding.tilNitrogen.hint = translatedHint
            }

            activity.translateText(binding.tilPhosphorus.hint.toString()) { translatedHint ->
                binding.tilPhosphorus.hint = translatedHint
            }

            activity.translateText(binding.tilPotassium.hint.toString()) { translatedHint ->
                binding.tilPotassium.hint = translatedHint
            }

            activity.translateText(binding.tilTemperature.hint.toString()) { translatedHint ->
                binding.tilTemperature.hint = translatedHint
            }

            activity.translateText(binding.tilHumidity.hint.toString()) { translatedHint ->
                binding.tilHumidity.hint = translatedHint
            }

            activity.translateText(binding.tilPH.hint.toString()) { translatedHint ->
                binding.tilPH.hint = translatedHint
            }

            activity.translateText(binding.tilRainfall.hint.toString()) { translatedHint ->
                binding.tilRainfall.hint = translatedHint
            }

            activity.translateText(binding.btnPredict.text.toString()) { translatedText ->
                binding.btnPredict.text = translatedText
            }
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Python environment
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(requireContext()))
        }

        val etNitrogen = view.findViewById<TextInputEditText>(R.id.etNitrogen)
        val etPhosphorus = view.findViewById<TextInputEditText>(R.id.etPhosphorus)
        val etPotassium = view.findViewById<TextInputEditText>(R.id.etPotassium)
        val etTemperature = view.findViewById<TextInputEditText>(R.id.etTemperature)
        val etHumidity = view.findViewById<TextInputEditText>(R.id.etHumidity)
        val etPH = view.findViewById<TextInputEditText>(R.id.etPH)
        val etRainfall = view.findViewById<TextInputEditText>(R.id.etRainfall)
        val btnPredict = view.findViewById<Button>(R.id.btnPredict)
        val tvPrediction = view.findViewById<TextView>(R.id.tvPrediction)
        val resultCard = view.findViewById<MaterialCardView>(R.id.resultCard)

        btnPredict.setOnClickListener {
            try {
                val nitrogen = etNitrogen.text.toString().toFloat()
                val phosphorus = etPhosphorus.text.toString().toFloat()
                val potassium = etPotassium.text.toString().toFloat()
                val temperature = etTemperature.text.toString().toFloat()
                val humidity = etHumidity.text.toString().toFloat()
                val ph = etPH.text.toString().toFloat()
                val rainfall = etRainfall.text.toString().toFloat()

                val inputData = listOf(
                    nitrogen, phosphorus, potassium, temperature, humidity, ph, rainfall
                )

                val python = Python.getInstance()
                val pythonModule = python.getModule("crop_prediction")
                val result = pythonModule.callAttr("predict_crop", inputData.toTypedArray()).toString()

                mainActivity?.translateText("Recommended Crop: $result") { translatedText ->
                    tvPrediction.text = translatedText
                    resultCard.visibility = View.VISIBLE
                }



            } catch (e: NumberFormatException) {
                Toast.makeText(requireContext(), "Please fill all fields with valid numbers", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }

        val mainActivity = activity as? MainActivity
        mainActivity?.translateAllViews(view)


        translateEditTextPlaceholders()
    }
}