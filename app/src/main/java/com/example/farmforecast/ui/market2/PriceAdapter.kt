package com.example.farmforecast.ui.market2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.farmforecast.MainActivity
import com.example.farmforecast.databinding.ItemPriceCardBinding

class PriceAdapter(
    private val prices: List<PriceData>,
    private val mainActivity: MainActivity?
) : RecyclerView.Adapter<PriceAdapter.PriceViewHolder>() {

    class PriceViewHolder(val binding: ItemPriceCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PriceViewHolder {
        val binding = ItemPriceCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PriceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PriceViewHolder, position: Int) {
        val price = prices[position]
        val binding = holder.binding

        // Set and translate commodity name
        mainActivity?.translateText(price.commodity) { translatedCommodity ->
            binding.commodityTextView.text = translatedCommodity
        } ?: run {
            binding.commodityTextView.text = price.commodity
        }

        // Set and translate market name
        mainActivity?.translateText("Market: ${price.market}") { translatedMarket ->
            binding.marketTextView.text = translatedMarket
        } ?: run {
            binding.marketTextView.text = "Market: ${price.market}"
        }

        // Set and translate district name
        mainActivity?.translateText("District: ${price.district}") { translatedDistrict ->
            binding.districtTextView.text = translatedDistrict
        } ?: run {
            binding.districtTextView.text = "District: ${price.district}"
        }

        // Format price range
        val priceRangeFormat = "Price Range: %s %s - %s "
        mainActivity?.translateText(String.format(priceRangeFormat, price.minPrice, price.maxPrice, price.modalPrice)) { translatedPrice ->
            binding.priceTextView.text = translatedPrice
        } ?: run {
            binding.priceTextView.text = String.format(priceRangeFormat, price.minPrice, price.maxPrice, price.modalPrice)
        }

        // Set and translate state
        mainActivity?.translateText("State: ${price.state}") { translatedState ->
            binding.stateTextView.text = translatedState
        } ?: run {
            binding.stateTextView.text = "State: ${price.state}"
        }
    }

    override fun getItemCount() = prices.size
}