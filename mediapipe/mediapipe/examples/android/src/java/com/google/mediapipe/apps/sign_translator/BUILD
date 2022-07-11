# Copyright 2019 The MediaPipe Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

licenses(["notice"])

# Libraries for Android
android_library(
    name = "basic_lib",
    srcs = glob(["*.java"]),
    manifest = "AndroidManifest.xml",
    resource_files = glob(["res/**"]),
    visibility = ["//visibility:public"],
    deps = [
        "//mediapipe/java/com/google/mediapipe/components:android_camerax_helper",
        "//mediapipe/java/com/google/mediapipe/components:android_components",
        "//mediapipe/java/com/google/mediapipe/framework:android_framework",
        "//mediapipe/java/com/google/mediapipe/glutil",
        "//third_party:androidx_appcompat",
        "//third_party:androidx_constraint_layout",
        "//third_party:opencv",
        "@maven//:androidx_concurrent_concurrent_futures",
        "@maven//:com_google_guava_guava",
	"@maven//:com_google_android_material_material"
    ],
)

# Manifest
exports_files(
    srcs = ["AndroidManifest.xml"],
)

# Native dependencies.
cc_binary(
    name = "libmediapipe_jni.so",
    linkshared = 1,
    linkstatic = 1,
    deps = [
	"//mediapipe/graphs/sign_translating:sign_translating_gpu_deps", 
	"//mediapipe/java/com/google/mediapipe/framework/jni:mediapipe_framework_jni",
    ],
)

# Converts the .so cc_binary into a cc_library, to be consumed in an android_binary.
cc_library(
    name = "mediapipe_jni_lib",
    srcs = [":libmediapipe_jni.so"],
    alwayslink = 1,
)

# Sign translator app.
android_binary(
    name = "signtranslator",
    assets = [
	"//mediapipe/graphs/sign_translating:sign_translating_gpu.binarypb",
	"//mediapipe/modules/face_detection:face_detection_short_range.tflite",
	"//mediapipe/modules/face_landmark:face_landmark.tflite",
	"//mediapipe/modules/hand_landmark:hand_landmark_full.tflite",
	"//mediapipe/modules/hand_landmark:handedness.txt",
	"//mediapipe/modules/sign_landmark:hand_recrop.tflite",
	"//mediapipe/modules/pose_detection:pose_detection.tflite",
	"//mediapipe/modules/pose_landmark:pose_landmark_full.tflite", 
	"//mediapipe/modules/sign_landmark:model.tflite",
	"//mediapipe/modules/sign_landmark:labelmap_sign.txt",
   ],
    assets_dir = "",
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "applicationId": "com.google.mediapipe.apps.sign_translator",
        "appName": "Sign Translator",
        "mainActivity": ".MainActivity",
        "cameraFacingFront": "False",
        "binaryGraphName": "sign_translating_gpu.binarypb",
        "inputVideoStreamName": "input_video",
        "outputVideoStreamName": "output_video",
        "flipFramesVertically": "True",
        "converterNumBuffers": "3",
    },
    multidex = "native",
    deps = [
        ":basic_lib",
        ":mediapipe_jni_lib",
	"//mediapipe/framework/formats:landmark_java_proto_lite",
	"//mediapipe/java/com/google/mediapipe/framework:android_framework",
    ],
)
