#ifndef CH0P_SCENE_MOTION_H
#define CH0P_SCENE_MOTION_H

#include <cstdint>
#include <vector>

namespace ch0p {

// Tunables for content-aware shot detection (PySceneDetect-equivalent defaults).
struct AnalyzerConfig {
    float contentThreshold = 27.0f;      // fixed-threshold cut trigger (0..255)
    float adaptiveRatio = 3.0f;          // spike / local-average ratio
    float adaptiveMinContent = 15.0f;    // floor so tiny spikes don't trigger
    int minSceneLenFrames = 15;          // debounce between cuts
    int adaptiveWindow = 2;              // +/- frames for the rolling average
};

// One analyzed (sampled) frame.
struct Sample {
    double tSec;          // presentation time
    float content;        // HSV mean-abs-diff vs previous frame, 0..255
    float motion;         // luma mean-abs-diff vs previous frame, normalized 0..1
    float sharpness;      // variance of Laplacian on luma (raw; normalized in Kotlin)
    float colorfulness;   // Hasler-Süsstrunk metric (raw; normalized in Kotlin)
};

// Feed sampled, downscaled RGB frames in order; then read shot boundaries + curves.
// No Android dependencies — builds and runs on the host for testing.
class SceneMotionAnalyzer {
public:
    explicit SceneMotionAnalyzer(AnalyzerConfig cfg);

    // rgb: interleaved R,G,B bytes, width*height*3. Frames must share dimensions.
    void pushFrame(const uint8_t* rgb, int width, int height, double tSec);

    // Shot-boundary timestamps (seconds), computed from accumulated samples.
    std::vector<double> cutTimes() const;

    const std::vector<Sample>& samples() const { return samples_; }

private:
    AnalyzerConfig cfg_;
    std::vector<Sample> samples_;
    std::vector<float> prevH_, prevS_, prevV_, prevLuma_;
    bool hasPrev_ = false;
};

}  // namespace ch0p

#endif  // CH0P_SCENE_MOTION_H
