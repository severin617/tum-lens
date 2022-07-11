#include "absl/memory/memory.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/str_join.h"
#include "mediapipe/calculators/util/my_missing_landmarks_to_render_data_calculator.pb.h"
#include "mediapipe/framework/calculator_framework.h"
#include "mediapipe/framework/calculator_options.pb.h"
#include "mediapipe/framework/formats/location_data.pb.h"
#include "mediapipe/framework/port/ret_check.h"
#include "mediapipe/util/color.pb.h"
#include "mediapipe/util/render_data.pb.h"
#include "mediapipe/framework/formats/classification.pb.h"
namespace mediapipe {

namespace {

constexpr char kMissingLandmarksTag[] = "MISSING_LANDMARK";
constexpr char kRenderDataTag[] = "RENDER_DATA";

constexpr char kSceneLabelLabel[] = "MISSING_INFO"; //WAS LABEL

// The ratio of detection label font height to the height of detection bounding
// box.
constexpr double kLabelToBoundingBoxRatio = 0.1;
// Perserve 2 decimal digits.
constexpr float kNumScoreDecimalDigitsMultipler = 100;

}  // namespace

class MyMissingLandmarksToRenderDataCalculator : public CalculatorBase {
 public:
  MyMissingLandmarksToRenderDataCalculator() {}
  ~MyMissingLandmarksToRenderDataCalculator() override {}
  MyMissingLandmarksToRenderDataCalculator(const MyMissingLandmarksToRenderDataCalculator&) =
      delete;
  MyMissingLandmarksToRenderDataCalculator& operator=(
      const MyMissingLandmarksToRenderDataCalculator&) = delete;

  static absl::Status GetContract(CalculatorContract* cc);

  absl::Status Open(CalculatorContext* cc) override;

  absl::Status Process(CalculatorContext* cc) override;

 private:
  static void SetRenderAnnotationColorThickness(
      const MyMissingLandmarksToRenderDataCalculatorOptions& options,
      RenderAnnotation* render_annotation);
  
  static void AddInfo(Classification classification,
                        const MyMissingLandmarksToRenderDataCalculatorOptions& options, RenderData* render_data);
  static void DrawRectangle(const MyMissingLandmarksToRenderDataCalculatorOptions& options,
                            RenderAnnotation* render_annotation);
};
REGISTER_CALCULATOR(MyMissingLandmarksToRenderDataCalculator);

absl::Status MyMissingLandmarksToRenderDataCalculator::GetContract(
    CalculatorContract* cc) {
    RET_CHECK(cc->Inputs().HasTag(kMissingLandmarksTag))
      << "No input streams named missing_landmarks is provided.";

    cc->Inputs().Tag(kMissingLandmarksTag).Set<ClassificationList>();
    cc->Outputs().Tag(kRenderDataTag).Set<RenderData>();
    return absl::OkStatus();
}

absl::Status MyMissingLandmarksToRenderDataCalculator::Open(CalculatorContext* cc) {
    cc->SetOffset(TimestampDiff(0));
    return absl::OkStatus();
}

absl::Status MyMissingLandmarksToRenderDataCalculator::Process(CalculatorContext* cc) {
    const auto& options = cc->Options<MyMissingLandmarksToRenderDataCalculatorOptions>();
    auto render_data = absl::make_unique<RenderData>();
    render_data->set_scene_class(options.scene_class());
    //AddInfo(cc->Inputs().Tag(kMissingLandmarksTag).Get<ClassificationList>().classification(0), options, render_data.get());

    cc->Outputs().Tag(kRenderDataTag).Add(render_data.release(), cc->InputTimestamp());
    return absl::OkStatus();
}

void MyMissingLandmarksToRenderDataCalculator::SetRenderAnnotationColorThickness(
    const MyMissingLandmarksToRenderDataCalculatorOptions& options,
    RenderAnnotation* render_annotation) {
    render_annotation->mutable_color()->set_r(options.color().r());
    render_annotation->mutable_color()->set_g(options.color().g());
    render_annotation->mutable_color()->set_b(options.color().b());
    render_annotation->set_thickness(options.thickness()*3);
}

void MyMissingLandmarksToRenderDataCalculator::DrawRectangle(
        const MyMissingLandmarksToRenderDataCalculatorOptions& options,
        RenderAnnotation* render_annotation){
    render_annotation->set_scene_tag(kSceneLabelLabel);
    //background_annotation->mutable_filled_rectangle();
    auto* filled_rectangle = render_annotation->mutable_filled_rectangle();
    auto* rectangle = filled_rectangle->mutable_rectangle();
    rectangle->set_left(10);
    rectangle->set_top(950);
    rectangle->set_right(1000);
    rectangle->set_bottom(1050);
    //annotation_renderer does not use fill color despite the render data proto's hint
    //filled_rectangle->mutable_fill_color()->set_r(options.fill_color().r());
    //filled_rectangle->mutable_fill_color()->set_g(options.fill_color().g());
    //filled_rectangle->mutable_fill_color()->set_b(options.fill_color().b());
    render_annotation->mutable_color()->set_r(options.fill_color().r());
    render_annotation->mutable_color()->set_g(options.fill_color().g());
    render_annotation->mutable_color()->set_b(options.fill_color().b());
}


void MyMissingLandmarksToRenderDataCalculator::AddInfo(
    Classification classification,
    const MyMissingLandmarksToRenderDataCalculatorOptions& options,
    RenderData* render_data) {

    auto* background_annotation = render_data->add_render_annotations();
    DrawRectangle(options, background_annotation);

    std::string info_str = "";
    int code = classification.index();
    switch (code) {
        case 0: info_str = "alles erkannt"; break;
        case 1: info_str = "Gesicht nicht erkannt"; break;
        case 2: info_str = "Linke Hand nicht erkannt"; break;
        case 3: info_str = "Linke Hand, Gesicht nicht erkannt"; break;
        case 4: info_str = "Rechte Hand nicht erkannt"; break;
        case 5: info_str = "Rechte Hand, Gesicht nicht erkannt"; break;
        case 6: info_str = "Beide Haende nicht erkannt"; break;
        case 7: info_str = "Beide Haende, Gesicht nicht erkannt"; break;
        case 8: info_str = "Pose nicht erkannt"; break;
        case 9: info_str = "Pose, Gesicht nicht erkannt"; break;
        case 10: info_str = "Pose, linke Hand nicht erkannt"; break;
        case 11: info_str = "Pose, linke Hand, Gesicht nicht erkannt"; break;
        case 12: info_str = "Pose, rechte Hand nicht erkannt"; break;
        case 13: info_str = "Pose, rechte Hand, Gesicht nicht erkannt"; break;
        case 14: info_str = "Pose, beide Haende nicht erkannt"; break;
        case 15: info_str = "Nichts erkannt"; break;
        default: info_str = "keine Info, unbekanntes Signal";
    }
    info_str += code;
    if(info_str.length() > 0){
        info_str.pop_back();
    }
    auto* info_annotation = render_data->add_render_annotations();
    info_annotation->set_scene_tag(kSceneLabelLabel);
    SetRenderAnnotationColorThickness(options, info_annotation);
    auto* text = info_annotation->mutable_text();
    *text = options.text();

    text->set_display_text(info_str);
    text->set_font_height(25);
    text->set_left(60);
    text->set_baseline(1000);
    text->set_font_face(0);
    //LOG(WARNING) << "OOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO" << info_str;
}


}  // namespace mediapipe
