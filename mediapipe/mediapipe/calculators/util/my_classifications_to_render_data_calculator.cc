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

#include "absl/memory/memory.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_join.h"
#include "mediapipe/calculators/util/my_classifications_to_render_data_calculator.pb.h"
#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/calculator_options.pb.h"
#include "mediapipe/framework/formats/classification.pb.h"
#include "mediapipe/framework/formats/location_data.pb.h"
#include "mediapipe/framework/port/ret_check.h"
#include "mediapipe/util/color.pb.h"
#include "mediapipe/util/render_data.pb.h"
namespace mediapipe {

namespace {

constexpr char kClassificationTag[] = "CLASSIFICATION";
constexpr char kClassificationsTag[] = "CLASSIFICATIONS";
constexpr char kClassificationListTag[] = "CLASSIFICATION_LIST";
constexpr char kRenderDataTag[] = "RENDER_DATA";

constexpr char kSceneLabelLabel[] = "CLASSIFICATION_TEXT"; //WAS LABEL
constexpr char kSceneFeatureLabel[] = "FEATURE";
constexpr char kSceneLocationLabel[] = "LOCATION";
constexpr char kKeypointLabel[] = "KEYPOINT";

// The ratio of detection label font height to the height of detection bounding
// box.
constexpr double kLabelToBoundingBoxRatio = 0.1;
// Perserve 2 decimal digits.
constexpr float kNumScoreDecimalDigitsMultipler = 100;

}  // namespace

// A calculator that converts Classification proto to RenderData proto for
// visualization.
//
// Classification is the format for encoding one or more classifications in an image.
// The input can be std::vector<Classification> or ClassificationList.
//
// Please note that only Location Data formats of BOUNDING_BOX and
// RELATIVE_BOUNDING_BOX are supported. Normalized coordinates for
// RELATIVE_BOUNDING_BOX must be between 0.0 and 1.0. Any incremental normalized
// coordinates calculation in this calculator is capped at 1.0.
//
// The text(s) for "label(_id),score" will be shown on top left
// corner of the bounding box. The text for "feature_tag" will be shown on
// bottom left corner of the bounding box.
//
// Example config:
// node {
//   calculator: "MyClassificationsToRenderDataCalculator"
//   input_stream: "CLASSIFICATION:classification"
//   input_stream: "CLASSIFICATIONS:classifications"
//   input_stream: "CLASSIFICATION_LIST:classification_list"
//   output_stream: "RENDER_DATA:render_data"
//   options {
//     [mediapipe.MyClassificationsToRenderDataCalculatorOptions.ext] {
//       produce_empty_packet : false
//     }
//   }
// }
class MyClassificationsToRenderDataCalculator : public CalculatorBase {
 public:
  MyClassificationsToRenderDataCalculator() {}
  ~MyClassificationsToRenderDataCalculator() override {}
  MyClassificationsToRenderDataCalculator(const MyClassificationsToRenderDataCalculator&) =
      delete;
  MyClassificationsToRenderDataCalculator& operator=(
      const MyClassificationsToRenderDataCalculator&) = delete;

  static absl::Status GetContract(CalculatorContract* cc);

  absl::Status Open(CalculatorContext* cc) override;

  absl::Status Process(CalculatorContext* cc) override;

 private:
  // These utility methods are supposed to be used only by this class. No
  // external client should depend on them. Due to C++ style guide unnamed
  // namespace should not be used in header files. So, these has been defined
  // as private static methods.
  //static std::string my_labels[3];
  static void SetRenderAnnotationColorThickness(
      const MyClassificationsToRenderDataCalculatorOptions& options,
      RenderAnnotation* render_annotation);

  static void SetTextCoordinate(bool normalized, double left, double baseline,
                                RenderAnnotation::Text* text);

  static void SetRectCoordinate(bool normalized, double xmin, double ymin,
                                double width, double height,
                                RenderAnnotation::Rectangle* rect);
  static void AddLabels(const Classification& classification,
                        const MyClassificationsToRenderDataCalculatorOptions& options,
                        float text_line_height, RenderData* render_data, int index);
  static void AddFeatureTag(
      const Classification& classification,
      const MyClassificationsToRenderDataCalculator& options,
      float text_line_height, RenderData* render_data);
  static void AddLocationData(
      const Classification& classification,
      const MyClassificationsToRenderDataCalculatorOptions& options,
      RenderData* render_data);
  static void AddDetectionToRenderData(
      const Classification& classification,
      const MyClassificationsToRenderDataCalculatorOptions& options,
      RenderData* render_data, int index);
};
REGISTER_CALCULATOR(MyClassificationsToRenderDataCalculator);

absl::Status MyClassificationsToRenderDataCalculator::GetContract(
    CalculatorContract* cc) {
  RET_CHECK(cc->Inputs().HasTag(kClassificationListTag) ||
            cc->Inputs().HasTag(kClassificationsTag) ||
            cc->Inputs().HasTag(kClassificationTag))
      << "None of the input streams are provided.";

  if (cc->Inputs().HasTag(kClassificationTag)) {
    cc->Inputs().Tag(kClassificationTag).Set<Classification>();
  }
  if (cc->Inputs().HasTag(kClassificationListTag)) {
    cc->Inputs().Tag(kClassificationListTag).Set<ClassificationList>();
  }
  if (cc->Inputs().HasTag(kClassificationsTag)) {
    cc->Inputs().Tag(kClassificationsTag).Set<std::vector<Classification>>();
  }
  cc->Outputs().Tag(kRenderDataTag).Set<RenderData>();
  return absl::OkStatus();
}

absl::Status MyClassificationsToRenderDataCalculator::Open(CalculatorContext* cc) {
  cc->SetOffset(TimestampDiff(0));
  //for(int i = 0; i < 3; i++){
  //    my_labels[i] = "";
  //}
  return absl::OkStatus();
}

absl::Status MyClassificationsToRenderDataCalculator::Process(CalculatorContext* cc) {
  const auto& options = cc->Options<MyClassificationsToRenderDataCalculatorOptions>();
  const bool has_detection_from_list =
      cc->Inputs().HasTag(kClassificationListTag) && !cc->Inputs()
                                                     .Tag(kClassificationListTag)
                                                     .Get<ClassificationList>()
                                                     .classification()
                                                     .empty();
  const bool has_detection_from_vector =
      cc->Inputs().HasTag(kClassificationsTag) &&
      !cc->Inputs().Tag(kClassificationsTag).Get<std::vector<Classification>>().empty();
  const bool has_single_detection = cc->Inputs().HasTag(kClassificationTag) &&
                                    !cc->Inputs().Tag(kClassificationTag).IsEmpty();
  if (!options.produce_empty_packet() && !has_detection_from_list &&
      !has_detection_from_vector && !has_single_detection) {
    return absl::OkStatus();
  }

  // TODO: Add score threshold to
  // MyClassificationsToRenderDataCalculatorOptions.
  auto render_data = absl::make_unique<RenderData>();
  render_data->set_scene_class(options.scene_class());
  if (has_detection_from_list) {
      int counter = 0;
    for (const auto& classification :
         cc->Inputs().Tag(kClassificationListTag).Get<ClassificationList>().classification()) {
      //AddDetectionToRenderData(classification, options, render_data.get(), counter++);
    }
  }
  if (has_detection_from_vector) {
      int counter = 0;
    for (const auto& classification :
         cc->Inputs().Tag(kClassificationsTag).Get<std::vector<Classification>>()) {
      AddDetectionToRenderData(classification, options, render_data.get(), counter++);
    }
  }
  if (has_single_detection) {
    AddDetectionToRenderData(cc->Inputs().Tag(kClassificationTag).Get<Classification>(),
                             options, render_data.get(), 0);
  }
  cc->Outputs()
      .Tag(kRenderDataTag)
      .Add(render_data.release(), cc->InputTimestamp());
  return absl::OkStatus();
}

void MyClassificationsToRenderDataCalculator::SetRenderAnnotationColorThickness(
    const MyClassificationsToRenderDataCalculatorOptions& options,
    RenderAnnotation* render_annotation) {
  render_annotation->mutable_color()->set_r(options.color().r());
  render_annotation->mutable_color()->set_g(options.color().g());
  render_annotation->mutable_color()->set_b(options.color().b());
  render_annotation->set_thickness(options.thickness()*8);
}

void MyClassificationsToRenderDataCalculator::SetTextCoordinate(
    bool normalized, double left, double baseline,
    RenderAnnotation::Text* text) {
  text->set_normalized(normalized);
  text->set_left(normalized ? std::max(left, 0.0) : left);
  // Normalized coordinates must be between 0.0 and 1.0, if they are used.
  text->set_baseline(normalized ? std::min(baseline, 1.0) : baseline);
}

void MyClassificationsToRenderDataCalculator::SetRectCoordinate(
    bool normalized, double xmin, double ymin, double width, double height,
    RenderAnnotation::Rectangle* rect) {
  if (xmin + width < 0.0 || ymin + height < 0.0) return;
  if (normalized) {
    if (xmin > 1.0 || ymin > 1.0) return;
  }
  rect->set_normalized(normalized);
  rect->set_left(normalized ? std::max(xmin, 0.0) : xmin);
  rect->set_top(normalized ? std::max(ymin, 0.0) : ymin);
  // No "xmin + width -1" because the coordinates can be relative, i.e. [0,1],
  // and we don't know what 1 pixel means in term of double [0,1].
  // For consistency decided to not decrease by 1 also when it is not relative.
  // However, when the coordinate is normalized it has to be between 0.0 and
  // 1.0.
  rect->set_right(normalized ? std::min(xmin + width, 1.0) : xmin + width);
  rect->set_bottom(normalized ? std::min(ymin + height, 1.0) : ymin + height);
}

void MyClassificationsToRenderDataCalculator::AddLabels(
    const Classification& classification,
    const MyClassificationsToRenderDataCalculatorOptions& options,
    float text_line_height, RenderData* render_data, int index) {

  const auto num_labels = 1;

  // Extracts all "label(_id),score" for the detection.
  std::vector<std::string> label_and_scores = {};

  std::string label_str = classification.label();
  //const float rounded_score = std::round(classification.score() * kNumScoreDecimalDigitsMultipler) /
  //        kNumScoreDecimalDigitsMultipler;
  if(label_str.length() > 0){
      label_str.pop_back();
  }
  const float rounded_score = classification.score();
  double lf = rounded_score;
  int iSigned = lf > 0? 1: -1;
  unsigned int uiTemp = (lf*pow(10, 2)) * iSigned;
  lf = (((double)uiTemp)/pow(10,2) * iSigned);
  double percentage = lf * 100;

  std::string label_and_score = absl::StrCat(label_str," : ", percentage, "%");
  //LOG(WARNING) << "STRIIIIIIIIIIIIIIIIIIIING:" << label_and_score;

  //TODO only show label
  label_and_scores.push_back(label_and_score);
  //label_and_scores.push_back(label_str);

  std::vector<std::string> labels;

  /*if (options.one_label_per_line()) {
    labels.insert(labels.end(), label_and_scores.begin(),
                  label_and_scores.end());
  } else {
    labels.push_back(absl::StrJoin(label_and_scores, ""));
  }*/
  //for(int i = 1; i < 3; i++){
  //    my_labels[i] = my_labels[i-1];
  //}
  //my_labels[0] = absl::StrJoin(label_and_scores, "");
  labels.push_back(absl::StrJoin(label_and_scores, ""));

  //labels.push_back(label_and_scores);
  // Add the render annotations for "label(_id),score".
  for (int i = 0; i < labels.size(); ++i) {
    auto label = labels.at(i);
    //auto label = my_labels[i];
    auto* label_annotation = render_data->add_render_annotations();
    //label_annotation->set_scene_tag(kSceneLabelLabel);
    SetRenderAnnotationColorThickness(options, label_annotation);
    auto* text = label_annotation->mutable_text();
    *text = options.text();
    text->set_display_text(label);
    text->set_font_height(48);
    text->set_left(60);
    text->set_baseline(400 + (index) * 100);
  }
}

void MyClassificationsToRenderDataCalculator::AddLocationData(
        const Classification& classification,
        const MyClassificationsToRenderDataCalculatorOptions& options,
        RenderData* render_data) {
    auto* location_data_annotation = render_data->add_render_annotations();
    location_data_annotation->set_scene_tag(kSceneLocationLabel);
    SetRenderAnnotationColorThickness(options, location_data_annotation);
    auto* location_data_rect = location_data_annotation->mutable_rectangle();

}


void MyClassificationsToRenderDataCalculator::AddDetectionToRenderData(
    const Classification& classification,
    const MyClassificationsToRenderDataCalculatorOptions& options,
    RenderData* render_data, int index) {

  double text_line_height = 1.0;

  AddLabels(classification, options, text_line_height, render_data, index);
  //AddFeatureTag(detection, options, text_line_height, render_data);
  //AddLocationData(classification, options, render_data);
}
}  // namespace mediapipe
