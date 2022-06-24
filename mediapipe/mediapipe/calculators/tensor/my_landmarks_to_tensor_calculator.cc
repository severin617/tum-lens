// Copyright 2021 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//#include "mediapipe/calculators/tensor/landmarks_to_tensor_calculator.h"

#include <memory>
#include <signal.h>
#include <stdexcept>
#include <iostream>
#include "mediapipe/calculators/tensor/landmarks_to_tensor_calculator.pb.h"
#include "mediapipe/framework/api2/node.h"
#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/formats/landmark.pb.h"
#include "mediapipe/framework/formats/tensor.h"
#include "mediapipe/framework/port/ret_check.h"
#include "tensorflow/lite/interpreter.h"
#include "mediapipe/framework/formats/matrix.h"


namespace mediapipe {
namespace api2 {

namespace {

float GetAttribute(
    const NormalizedLandmark& landmark,
    const LandmarksToTensorCalculatorOptions::Attribute& attribute) {
  switch (attribute) {
    case LandmarksToTensorCalculatorOptions::X:
      return landmark.x();
    case LandmarksToTensorCalculatorOptions::Y:
      return landmark.y();
    case LandmarksToTensorCalculatorOptions::Z:
      return landmark.z();
    case LandmarksToTensorCalculatorOptions::VISIBILITY:
      return landmark.visibility();
    case LandmarksToTensorCalculatorOptions::PRESENCE:
      return landmark.presence();
  }
}

}  // namespace

//namespace tf = tensorflow;
//puts landmarks into a matrix which will be converted to tensors in the next calculator
class LandmarksToTensorCalculator: public CalculatorBase {
 public:

    static absl::Status GetContract(CalculatorContract* cc){
        cc->Inputs().Index(0).Set<NormalizedLandmarkList>();
        //cc->Outputs().Index(0).Set<std::vector<TfLiteTensor>>();
        cc->Outputs().Index(0).Set<Matrix>();
        return absl::OkStatus();
    }

    absl::Status Open(CalculatorContext* cc) override {
        // TODO might need cc->SetOffset(TimestampDiff(0));
        // this will output a packet whenever one is received
        cc->SetOffset(TimestampDiff(0));
        options_ = cc->Options<LandmarksToTensorCalculatorOptions>();
        RET_CHECK(options_.attributes_size() > 0)
            << "At least one attribute must be specified";
        //FOR CPU
        interpreter_ = absl::make_unique<tflite::Interpreter>();
        interpreter_->AddTensors(1);
        interpreter_->SetInputs({0});
        return absl::OkStatus();
    }

    absl::Status Process(CalculatorContext* cc) override {
        if (cc->Inputs().Index(0).IsEmpty()) {
          return absl::OkStatus();
        }

        // Get input landmarks.
        const auto& in_landmarks = cc->Inputs().Index(0).Get<NormalizedLandmarkList>();
        const int n_landmarks = in_landmarks.landmark_size();
        const int n_attributes = options_.attributes_size();
        const int channels = 1;

        //TODO create matrix from list

        auto output = absl::make_unique<Matrix>(n_landmarks, n_attributes);

        auto tensor_shape = Tensor::Shape{1, n_landmarks, n_attributes};

        // Create empty tensor.
        Tensor tensor(Tensor::ElementType::kFloat32, tensor_shape);
        auto* buffer = tensor.GetCpuWriteView().buffer<float>();

        // Fill tensor with landmark attributes.
        for (int i = 0; i < n_landmarks; ++i) {
            //TODO keep only x and y (n_attributes set to 2
            for (int j = 0; j < 2; ++j) {
                buffer[i * 2 + j] =
                        GetAttribute(in_landmarks.landmark(i), options_.attributes(j));
            }
        }

        *output = Eigen::MatrixXf::Map(buffer, n_landmarks, n_attributes);

        //LOG(WARNING) << "number landmarks: " << n_landmarks;
        cc->Outputs().Index(0).Add(output.release(), cc->InputTimestamp());

        return absl::OkStatus();
    }

    absl::Status Close(CalculatorContext* cc){
        return absl::OkStatus();
    }

 private:
    LandmarksToTensorCalculatorOptions options_;
    std::unique_ptr<tflite::Interpreter> interpreter_ = nullptr;
};
REGISTER_CALCULATOR(LandmarksToTensorCalculator);

}  // namespace api2
}  // namespace mediapipe
