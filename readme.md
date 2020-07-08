# Allgemeine Information
Habe das Projekt nochmal neu aufgesetzt. App enthält aktuell nur noch den *Live-Classification-Modus*.

*Stand vom 08.07.2020*

---

# Getting Started

### 1 Activate Developer Mode
To enable developer options on your Android phone, tap the Build Number option 7 times.
You can find this option in one of the following locations, depending on your Android version:

* Android 9 (API level 28) and higher: Settings > About Phone > Build Number
* Android 8.0.0 (API level 26) and Android 8.1.0 (API level 26): Settings > System > About Phone > Build Number

Further reading: https://developer.android.com/studio/debug/dev-options


### 2 Clone Repository 
`git clone git@gitlab.lrz.de:jmax/lens.git`

Please note: repository contains `.tflite` models in `/assets` folder. This affects its size.

### 3 Run the App
Open up Android Studio and import the previously cloned project.

Connect your phone to your computer via laptop. It needs to appear in the drop down in the upper menu bar of Android Studio.

Now, run the project. The app should automatically launch on your phone.

At the moment, you can change `.tflite` model, `numOfThreads` used and `hardware deligate` only by modifying `Classifier.java`.
