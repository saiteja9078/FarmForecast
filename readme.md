# FarmForecast

FarmForecast is a comprehensive mobile application designed to assist farmers with crucial agricultural information, including plant disease diagnosis, crop recommendations, and market prices. The app uses advanced technologies such as machine learning for plant disease detection and crop recommendation based on soil and environmental parameters.

## Features

### 1. Leaf Disease Diagnosis

Diagnose plant diseases by taking photos of plant leaves or selecting images from the gallery. The app uses a TensorFlow Lite model to:
- Identify common diseases across multiple crops
- Provide detailed diagnosis results
- Suggest appropriate treatments for identified diseases
- Support for 38 different plant disease conditions across various crops

### 2. Market Price Information

Access up-to-date commodity price information from agricultural markets:
- Search for prices by commodity, state, and market
- View minimum, modal, and maximum prices
- Filter results by specific markets
- Data retrieved from online agricultural market platforms

### 3. Crop Recommendation

Get personalized crop suggestions based on soil and environmental parameters:
- Input soil NPK (Nitrogen, Phosphorus, Potassium) values
- Enter environmental factors (temperature, humidity, pH, rainfall)
- Receive recommendations for suitable crops based on provided data
- Python-based prediction model for accurate suggestions

### 4. Multilingual Support

The app is designed to be accessible to farmers across different regions with full multilingual support:
- Automatic translation of all UI elements
- Support for multiple Indian languages (Hindi, Telugu, Tamil, Kannada, Marathi, Gujarati, Urdu)
- English as the fallback language
- Uses Google ML Kit for translations

## Technical Details

### Architecture & Components

- **Language**: Kotlin
- **Machine Learning**:
  - TensorFlow Lite for disease diagnosis
  - Python integration using Chaquopy for crop recommendations
- **Networking**: OkHttp for API requests
- **Web Scraping**: JSoup for market price extraction
- **Translation**: Google ML Kit Translate
- **UI Components**: Material Design components
- **Navigation**: Jetpack Navigation Component
- **Image Processing**: Android's built-in image manipulation tools

### Model Information

The plant disease diagnosis model (`trained_plant_disease_model.tflite`):
- 128x128 input image size
- 38 different classification categories covering various crop diseases
- Processes images on-device for privacy and offline operation

## Setup Instructions

### Prerequisites

- Android Studio Arctic Fox (2020.3.1) or newer
- Minimum SDK: 21 (Android 5.0)
- Target SDK: 33 (Android 13)
- Gradle version: 7.0.0+

### Dependencies

Add these dependencies to your `build.gradle` file:

```gradle
// TensorFlow Lite
implementation 'org.tensorflow:tensorflow-lite:2.9.0'
implementation 'org.tensorflow:tensorflow-lite-support:0.4.2'

// ML Kit Translation
implementation 'com.google.mlkit:translate:17.0.1'

// Python integration
implementation 'com.chaquo.python:android:14.0.0'

// Networking & parsing
implementation 'com.squareup.okhttp3:okhttp:4.9.3'
implementation 'org.jsoup:jsoup:1.15.3'

// UI components
implementation 'com.google.android.material:material:1.8.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.navigation:navigation-fragment-ktx:2.5.3'
implementation 'androidx.navigation:navigation-ui-ktx:2.5.3'
```

### Model Setup

1. Place the `trained_plant_disease_model.tflite` file in the `app/src/main/assets/` directory
2. Place the Python crop prediction script (`crop_prediction.py`) in the `app/src/main/python/` directory

### Python Script Configuration

Enable Chaquopy in your app-level `build.gradle`:

```gradle
android {
    defaultConfig {
        ndk {
            abiFilters "armeabi-v7a", "arm64-v8a", "x86", "x86_64"
        }
        python {
            buildPython "pip install scikit-learn numpy pandas"
        }
    }
}
```

## Usage

### Leaf Disease Diagnosis

1. Navigate to the "Leaf Diagnosis" tab
2. Tap on the image card to select a source (camera or gallery)
3. Take or select a photo of a plant leaf showing symptoms
4. Tap "Analyze" to get the diagnosis results and treatment recommendations

### Market Prices

1. Navigate to the "Market Prices" tab
2. Enter the commodity name (e.g., "wheat", "rice")
3. Enter the state name
4. Optionally enter a specific market
5. Tap "Search" to retrieve current prices

### Crop Recommendation

1. Navigate to the "Crop Advisor" tab
2. Enter soil NPK values
3. Input environmental parameters (temperature, humidity, pH, rainfall)
4. Tap "Predict" to receive crop recommendations

## Permissions

The app requires the following permissions:

- `CAMERA` - For capturing leaf images
- `READ_EXTERNAL_STORAGE` or `READ_MEDIA_IMAGES` - For selecting images from gallery
- `INTERNET` - For fetching market prices and translations
- `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` - For weather information (if implemented)
