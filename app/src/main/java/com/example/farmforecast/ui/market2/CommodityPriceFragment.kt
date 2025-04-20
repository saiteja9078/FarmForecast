package com.example.farmforecast.ui.market2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.farmforecast.MainActivity
import com.example.farmforecast.R
import com.example.farmforecast.databinding.FragmentCommodityPriceBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

class CommodityPriceFragment : Fragment() {
    private var _binding: FragmentCommodityPriceBinding? = null
    private val binding get() = _binding!!

    private lateinit var priceAdapter: PriceAdapter
    private val priceList = mutableListOf<PriceData>()

    // Get reference to MainActivity for translation functions
    private val mainActivity: MainActivity?
        get() = activity as? MainActivity

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommodityPriceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()



        translateEditTextPlaceholders()

        binding.searchButton.setOnClickListener {
            val commodity = binding.commodityEditText.text.toString().trim()
            val state = binding.stateEditText.text.toString().trim()
            val market = binding.marketEditText.text.toString().trim()

            if (commodity.isEmpty() || state.isEmpty()) {
                mainActivity?.translateText("Please enter commodity and state") { translatedMessage ->
                    Toast.makeText(context, translatedMessage, Toast.LENGTH_SHORT).show()
                } ?: Toast.makeText(context, "Please enter commodity and state", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            searchPrices(commodity, state, market)
        }
        val mainActivity = activity as? MainActivity
        mainActivity?.translateAllViews(view)

    }

    private fun translateEditTextPlaceholders() {
        mainActivity?.let { activity ->
            // Translate TextInputLayout hints (these become the floating labels)
            val commodityHint = binding.commodityInputLayout.hint.toString()
            activity.translateText(commodityHint) { translatedHint ->
                binding.commodityInputLayout.hint = translatedHint
            }

            val stateHint = binding.stateInputLayout.hint.toString()
            activity.translateText(stateHint) { translatedHint ->
                binding.stateInputLayout.hint = translatedHint
            }

            val marketHint = binding.marketInputLayout.hint.toString()
            activity.translateText(marketHint) { translatedHint ->
                binding.marketInputLayout.hint = translatedHint
            }

            // Translate Button text
            activity.translateText(binding.searchButton.text.toString()) { translatedText ->
                binding.searchButton.text = translatedText
            }
        }
    }

    private fun setupRecyclerView() {
        priceAdapter = PriceAdapter(priceList, mainActivity)
        binding.priceRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = priceAdapter
        }
    }

    private fun searchPrices(commodity: String, state: String, market: String) {
        showLoading()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val formattedCommodity = commodity.lowercase().replace(" ", "-")
                val formattedState = state.lowercase().replace(" ", "-")
                val formattedMarket = market.lowercase().replace(" ","-")

                val url = "https://www.commodityonline.com/mandiprices/$formattedCommodity/$formattedState/"
                val html = fetchHtmlWithRetry(url)

                if (html == null) {
                    withContext(Dispatchers.Main) {
                        mainActivity?.translateText("Failed to fetch data. Please try again.") { translatedError ->
                            showError(translatedError)
                        } ?: showError("Failed to fetch data. Please try again.")
                    }
                    return@launch
                }

                val prices = parsePriceData(html, market)

                withContext(Dispatchers.Main) {
                    if (prices.isEmpty()) {
                        mainActivity?.translateText("No data found for the specified criteria.") { translatedError ->
                            showError(translatedError)
                        } ?: showError("No data found for the specified criteria.")
                    } else {
                        // Translate price data before updating UI
                        translatePriceData(prices) { translatedPrices ->
                            updatePriceList(translatedPrices)
                            showResults()
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    mainActivity?.translateText("Error: ${e.message}") { translatedError ->
                        showError(translatedError)
                    } ?: showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun translatePriceData(prices: List<PriceData>, callback: (List<PriceData>) -> Unit) {
        val activity = mainActivity
        if (activity == null) {
            callback(prices)
            return
        }

        // Extract all strings that need translation
        val stringsToTranslate = mutableListOf<String>()
        for (price in prices) {
            stringsToTranslate.add(price.state)
            stringsToTranslate.add(price.commodity)
            stringsToTranslate.add(price.market)
            stringsToTranslate.add(price.district)
        }

        // Translate them in batch
        activity.translateStrings(stringsToTranslate) { translatedStrings ->
            val translatedPrices = mutableListOf<PriceData>()

            // Reconstruct PriceData objects with translated strings
            for (i in prices.indices) {
                val original = prices[i]
                val idx = i * 4 // Each price item has 4 strings to translate

                translatedPrices.add(
                    PriceData(
                        state = translatedStrings[idx],
                        commodity = translatedStrings[idx + 1],
                        market = translatedStrings[idx + 2],
                        district = translatedStrings[idx + 3],
                        minPrice = original.minPrice,
                        modalPrice = original.modalPrice,
                        maxPrice = original.maxPrice
                    )
                )
            }

            callback(translatedPrices)
        }
    }

    private fun fetchHtmlWithRetry(url: String, maxRetries: Int = 3): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/112.0.0.0 Safari/537.36"
            )
            .build()

        repeat(maxRetries) { attempt ->
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val html = response.body?.string() ?: ""
                    if ("main-table2" in html) {
                        return html
                    }
                }
                Thread.sleep(1000) // Wait 1 second before retry
            } catch (e: Exception) {
                // Continue to next attempt
            }
        }
        return null
    }

    private fun parsePriceData(html: String, marketFilter: String): List<PriceData> {
        val result = mutableListOf<PriceData>()
        val document = Jsoup.parse(html)
        val table = document.getElementById("main-table2") ?: return result

        val rows = table.select("tr")
        if (rows.size <= 1) {
            return result
        }

        for (row in rows.drop(1))
        {
            val cols = row.select("td")
            if (cols.size >= 8) {
                val vegetable = cols[0].text().trim()
                val market = cols[5].text().trim()
                val state = cols[3].text().trim()
                val district = cols[4].text().trim()
                val price1 = ""
                val price2 = cols[6].text().trim()
                val price3 = cols[7].text().trim()

                // Apply market filter if provided
                if (marketFilter.isNotEmpty() && !market.contains(marketFilter, ignoreCase = true)) {
                    continue
                }

                result.add(
                    PriceData(
                        state = state,
                        commodity = vegetable,
                        market = market,
                        district = district,
                        minPrice = price1,
                        modalPrice = price2,
                        maxPrice = price3
                    )
                )
            }
        }

        return result
    }

    private fun updatePriceList(prices: List<PriceData>) {
        priceList.clear()
        priceList.addAll(prices)
        priceAdapter.notifyDataSetChanged()
    }

    private fun showLoading() {
        binding.apply {
            statusTextView.visibility = View.GONE
            priceRecyclerView.visibility = View.GONE
            progressBar.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            priceRecyclerView.visibility = View.GONE
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = message
        }
    }

    private fun showResults() {
        binding.apply {
            progressBar.visibility = View.GONE
            statusTextView.visibility = View.GONE
            priceRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

data class PriceData(
    val state: String,
    val commodity: String,
    val market: String,
    val district: String,
    val minPrice: String,
    val modalPrice: String,
    val maxPrice: String
)
