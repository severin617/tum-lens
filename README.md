# Running TUM Lens on your Android smartphone

### 1 Activate Developer Mode
1. Open Settings app.
2. If you run Android 9 or higher, go to "About Phone" and scroll down to "Build Number". Otherwise go to "System", then "About Phone" and scroll down to "Build Number".
3. Now, **tap 7 times** on **Build Number**.

Find additional information here: https://developer.android.com/studio/debug/dev-options

### 2 Clone Repository and open it in Android Studio
1. Clone the project with `git clone ...` inside a local repository or use GitLab's "Download Source Code" option.
2. In Android Studio, choose "File", "New" and "Import Project ..." and select the corresponding directory.
![](img/screenshot1.png)

### 3 Connect your phone via cable
It should appear under "running devices" within the drop down in the upper menu bar of Android Studio.
![](img/screenshot2.png)


### 4 Running the app
Build and run the app by clicking the green play button in the upper menu bar.
![](img/screenshot3.png)

---

# Using TUM Lens
TODO
- double tap to pause classification 

---

# Adding models
As a tool for rapid prototyping, TUM Lens is set up in such a way that additional models can be added without changing its Java code. However, the project must be re-build. 

1. Place your `.tflite` files in the `/assets` directory.
2. Open `nets.json` in `/assets`.
3. Find the `"nets": [ ... ]` array in that file.
4. Add your model to that array. You may want to use an existing object for reference. Please note the section below on mandatory parameters and precautions.
5. Now, save the file and re-build the project.


#### Mandatory JSON Parameters
| Parameter          | Use     |
| :-------------     | :----------: |
|  `name`            | display name in *model selector*             |
|  `top5accuracy`    | displayed as info in *model selector*        |
|  `image_mean`      | float models require additional normalization in image pre-processing. Use `127.5f` for float, but `0.0f` for quantized networks to bypass the normalization.  |
|  `image_std`       | use `127.5f` for float and `1.0f` for quantized networks.   |
|  `probability_mean`| float models don't need dequantization in post-processing, set to `0.0f` to bypass normalization. Use `0.0f` for quantized models as well  |
|  `probability_std` | use `1.0f` for float and `255.0f` for quantized networks   |
|  `filename`        | name of `.tflite` model file in `/assets`    |
|  `labels`          | name of `.txt` labels file in `/assets`      |

---

# Converting models to TF-Lite

TODO