import requests
import json

def get_gemini_response(commodity_type, commodity, state, market, api_key):
    """
    Function to call Gemini API and get agricultural market data.
    Works with the free plan using Google AI Studio API key.
    """
    try:
        # API endpoint (use the correct API version and model)
        url = f"https://generativelanguage.googleapis.com/v1/models/gemini-2.0-flash:generateContent?key={api_key}"

        # Headers
        headers = {
            "Content-Type": "application/json"
        }

        # Request body
        request_body = {
            "contents": [
                {
                    "parts": [
                        {
                            "text": f"Predict today's highest price for {commodity} in {market}, {state},"

                        }
                    ]
                }
            ]
        }

        # Make the API call
        response = requests.post(url, headers=headers, data=json.dumps(request_body))

        if response.status_code == 200:
            response_json = response.json()
            # Extract the text from the response
            if 'candidates' in response_json and len(response_json['candidates']) > 0:
                text = response_json['candidates'][0]['content']['parts'][0]['text']
                return text
            else:
                return "No data available in the response."
        else:
            return f"Error: API returned status code {response.status_code}. {response.text}"

    except Exception as e:
        return f"Error: {str(e)}"

def get_mock_response(commodity_type, commodity, state, market):
    """
    Function to return a mock response for testing without API calls.
    """
    return f"""
# Market Information for {commodity}

## {market}, {state}

**Current Average Price:** ₹45-60 per kg

**Price Trend:** Prices have been stable over the past week with a slight upward trend expected due to seasonal demand.

**Supply Status:** Good availability with fresh arrivals daily from nearby farming areas.

**Quality:** Most produce is of premium quality with some standard grade also available.

**Cultivation Tips:**
- Best planting season: July-August
- Water requirements: Moderate
- Harvest period: 90-120 days after sowing
- Common diseases to watch for: Leaf spot, root rot

**Market Insights:**
Local farmers report good yields this season. Demand is expected to increase in the coming weeks as festival season approaches.
"""
