# Taipei City Garbage Truck Tracking App

This is an Android application designed to help users track the real-time location of garbage trucks in Taipei City and provide garbage classification recognition functionality.

## Features

*   **Real-Time Map Display**: Shows the locations of nearby garbage trucks on the map.
*   **Weather Information**: Displays the current weather status of the user's local area on the main page.
*   **Detailed Information**: Tap on map markers to view detailed information about collection points, including the location name, truck number, and estimated arrival and departure times.
*   **Navigation**: Provides navigation to the selected collection point.
*   **Garbage Classification Recognition**: Use the camera to capture an image of garbage, and the app will utilize a TensorFlow Lite model to identify the type of garbage (paper, metal, plastic, glass, or general waste) and indicate whether it is recyclable.
*   **Favorites**: Users can log in and add frequently used collection points to "Favorites" for quick access.
*   **Auto Display**: Clicking an item in the "Favorites" list will automatically move the map to the location and display an information card.
*   **Theme Switching**: Supports light and dark modes, which can be toggled manually or follow the system settings to provide a comfortable visual experience.

## Tech Stack

*   **Language**: Kotlin
*   **Architecture**: MVVM (Fragment-based)
*   **UI**: XML Layouts, Material Design Components
*   **Asynchronous Processing**: Coroutines
*   **Maps**: Google Maps Platform
*   **Location Services**: Google Play Services Location
*   **Networking**: OkHttp
*   **Backend Services**: Firebase (Authentication, Firestore)
*   **Machine Learning**: TensorFlow Lite
*   **Data Sources**: 
    *   [Taipei City Government Open Data Platform](https://data.taipei/)
    *   [Central Weather Administration Open Data Platform](https://opendata.cwa.gov.tw/)

## Machine Learning Model

*   **TensorFlow Lite Model**: The app integrates a pre-trained TensorFlow Lite model for garbage classification. The model is sourced from [Kaggle](https://www.kaggle.com/code/vasantvohra1/using-cnn-test-accuracy-77/output?select=garbage.tflite).

## Setup and Run Instructions

1.  **Clone the Project**
2.  **Firebase Setup**:
    *   Create a new project in the [Firebase Console](https://console.firebase.google.com/).
    *   Enable Authentication (Email/Password login).
    *   Enable Firestore Database.
    *   Download your `google-services.json` file and place it in the `app/` directory.
3.  **Google Maps API Key**:
    *   Obtain your Maps SDK for Android API key from the [Google Cloud Console](https://console.cloud.google.com/).
    *   Add the API key to your `local.properties` file in the following format:
        ```properties
        MAPS_API_KEY=YOUR_API_KEY
        ```
    *   Ensure the `com.google.android.geo.API_KEY` meta-data in `app/src/main/AndroidManifest.xml` correctly references the API key.
4.  **Build and Run**:
    *   Open the project in Android Studio.
    *   Wait for Gradle to sync.
    *   Run the `app` module on your emulator or physical device.

## Attributions

*   Weather icons by [Weather Icons](https://erikflowers.github.io/weather-icons/).
