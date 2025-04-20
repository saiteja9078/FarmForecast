import numpy as np
import joblib
import os
from os.path import dirname, join

def predict_crop(input_data):
    model_path = join(dirname(__file__), 'random_forest_model_final.pkl')
    if not os.path.exists(model_path):
        return f"Error: Model file not found at {model_path}"
    try:
        model = joblib.load(model_path)
        features = np.array(input_data).reshape(1, -1)
        prediction = model.predict(features)

        # Debugging: Check the type and shape of the prediction
        print(f"Prediction: {prediction}, Type: {type(prediction)}, Shape: {getattr(prediction, 'shape', 'No shape attribute')}")
        classes = ['apple', 'banana', 'blackgram', 'chickpea', 'coconut', 'coffee',
                   'cotton', 'grapes', 'jute', 'kidneybeans', 'lentil', 'maize', 'mango',
                   'mothbeans', 'mungbean', 'muskmelon', 'orange', 'papaya', 'pigeonpeas',
                   'pomegranate', 'rice', 'watermelon']

        return classes[np.argmax(prediction)]

    except Exception as e:
        return f"Error: {str(e)}"
