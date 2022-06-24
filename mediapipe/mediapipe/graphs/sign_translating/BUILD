load(
    "//mediapipe/framework/tool:mediapipe_graph.bzl",
    "mediapipe_binary_graph",
    "mediapipe_simple_subgraph",
)

package(default_visibility = ["//visibility:public"])

licenses(["notice"])

cc_library(
    name = "sign_translating_gpu_deps",
    deps = [
        "//mediapipe/calculators/core:flow_limiter_calculator",
        "//mediapipe/calculators/image:image_properties_calculator",
        "//mediapipe/calculators/util:annotation_overlay_calculator",
        "//mediapipe/modules/sign_landmark:holistic_landmark_gpu",
	"//mediapipe/calculators/core:concatenate_vector_calculator",
    ],
)

mediapipe_binary_graph(
    name = "sign_translating_gpu",
    graph = "sign_translating_gpu.pbtxt",
    output_name = "sign_translating_gpu.binarypb",
    deps = [":sign_translating_gpu_deps"],
)

cc_library(
    name = "sign_translating_cpu_graph_deps",
    deps = [
        "//mediapipe/calculators/core:flow_limiter_calculator",
        "//mediapipe/calculators/image:image_properties_calculator",
        "//mediapipe/calculators/util:annotation_overlay_calculator",
        "//mediapipe/modules/sign_landmark:holistic_landmark_cpu",
    ],
)
