// Copyright 2019 The MediaPipe Authors.
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

#ifndef MEDIAPIPE_CALCULATORS_CORE_MY_CONCATENATE_NORMALIZED_LIST_CALCULATOR_H_  // NOLINT
#define MEDIAPIPE_CALCULATORS_CORE_MY_CONCATENATE_NORMALIZED_LIST_CALCULATOR_H_  // NOLINT

#include "mediapipe/calculators/core/my_concatenate_vector_calculator.pb.h"
#include "mediapipe/framework/api2/node.h"
#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/formats/landmark.pb.h"
#include "mediapipe/framework/formats/classification.pb.h"
#include "mediapipe/framework/port/canonical_errors.h"
#include "mediapipe/framework/port/ret_check.h"
#include "mediapipe/framework/port/status.h"
#include <vector>
#include <cmath>

namespace mediapipe {
namespace api2 {

// Concatenates several NormalizedLandmarkList protos following stream index
// order. This class assumes that every input stream contains a
// NormalizedLandmarkList proto object.
class MyConcatenateNormalizedLandmarkListCalculator : public Node {
 public:
  static constexpr Input<NormalizedLandmarkList>::Multiple kIn{""};
  static constexpr Output<NormalizedLandmarkList> kOut{""};
  static constexpr Output<ClassificationList> signal{"SIGNAL"};

  MEDIAPIPE_NODE_CONTRACT(kIn, kOut, signal);

  static absl::Status UpdateContract(CalculatorContract* cc) {
    RET_CHECK_GE(kIn(cc).Count(), 1);
    return absl::OkStatus();
  }

  absl::Status Open(CalculatorContext* cc) override {
    only_emit_if_all_present_ =
          cc->Options<::mediapipe::MyConcatenateVectorCalculatorOptions>()
                  .only_emit_if_all_present();
    skip_face_landmarks_ = cc->Options<::mediapipe::MyConcatenateVectorCalculatorOptions>().skip_face_landmarks();
    return absl::OkStatus();
  }

  absl::Status Process(CalculatorContext* cc) override {
      int counter = 0;
      std::vector<NormalizedLandmarkList> all_lists;
    if (only_emit_if_all_present_) {
        int signal = 0;
        bool found_empty = false;
        for (const auto& input : kIn(cc)) {
            //skip face
          if(skip_face_landmarks_ && counter == 0){
              counter++;
              continue;
          }
          if (input.IsEmpty()){
              found_empty = true;
              signal += pow(2, counter);
          }
          counter++;
        }
        if(found_empty){
            auto classification_list = absl::make_unique<ClassificationList>();
            Classification* classification = classification_list->add_classification();
            classification->set_index(signal);
            classification->set_label("");
            classification->set_score(1);
            cc -> Outputs().Tag("SIGNAL").Add(classification_list.release(), cc->InputTimestamp());
            return absl::OkStatus();
        }
    }

    counter = 0;
    for (const auto& input : kIn(cc)) {
        if (input.IsEmpty()) {
            all_lists.push_back(createDummy(counter));
        }else{
            all_lists.push_back(*input);
        }
    }

    counter = 0;
    NormalizedLandmarkList output;
    for (const auto& input : all_lists) {
        //TODO skip face landmarks
        if(counter == 0){
            counter++;
            continue;
        }
        const NormalizedLandmarkList& list = input;
        for (int j = 0; j < list.landmark_size(); ++j) {
          *output.add_landmark() = list.landmark(j);
        }
    }
    kOut(cc).Send(std::move(output));
    return absl::OkStatus();
  }

  /* creates dummy of normalized landmark list
   * index 0 face 468 landmarks
   * index 1 left hand 21 landmarks
   * index 2 right hand 21 landmarks
   * index 3 pose 33 landmarks
   */
  NormalizedLandmarkList createDummy(int index){
    NormalizedLandmarkList dummy;
    int size = 0;
    switch (index) {
      case 0: size = 468; break;
      case 1: size = 21; break;
      case 2: size = 21; break;
      case 3: size = 33; break;
        default: size = 0;

    }
    for(int i = 0; i < size; i++){
      auto* landmark = dummy.add_landmark();
      landmark->set_x(-1.);
      landmark->set_y(-1.);
      landmark->set_z(-1.);
      landmark->set_visibility(-1.);
      landmark->set_presence(-1.);
    }
    return dummy;
  }
private:
    bool only_emit_if_all_present_;
    bool skip_face_landmarks_;
};
MEDIAPIPE_REGISTER_NODE(MyConcatenateNormalizedLandmarkListCalculator);

}  // namespace api2
}  // namespace mediapipe

// NOLINTNEXTLINE
#endif  // MEDIAPIPE_CALCULATORS_CORE_CONCATENATE_NORMALIZED_LIST_CALCULATOR_H_
