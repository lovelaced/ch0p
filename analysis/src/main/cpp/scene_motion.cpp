#include "scene_motion.h"

#include <algorithm>
#include <cmath>

namespace ch0p {

namespace {

// RGB (0..255) -> HSV with all channels scaled to 0..255. Hue is the standout cut signal
// because it is largely invariant to exposure/brightness flicker.
inline void rgbToHsv(uint8_t r, uint8_t g, uint8_t b, float& h, float& s, float& v) {
    const float rf = r / 255.0f, gf = g / 255.0f, bf = b / 255.0f;
    const float mx = std::max({rf, gf, bf});
    const float mn = std::min({rf, gf, bf});
    const float delta = mx - mn;

    v = mx * 255.0f;
    s = (mx <= 0.0f) ? 0.0f : (delta / mx) * 255.0f;

    float hue = 0.0f;  // degrees
    if (delta > 1e-6f) {
        if (mx == rf)      hue = 60.0f * std::fmod(((gf - bf) / delta), 6.0f);
        else if (mx == gf) hue = 60.0f * (((bf - rf) / delta) + 2.0f);
        else               hue = 60.0f * (((rf - gf) / delta) + 4.0f);
        if (hue < 0.0f) hue += 360.0f;
    }
    h = (hue / 360.0f) * 255.0f;
}

// Variance of the 4-neighbour Laplacian over a luma plane — a standard focus/sharpness
// measure (low variance = blurry/out-of-focus).
float computeSharpness(const std::vector<float>& luma, int width, int height) {
    if (width < 3 || height < 3) return 0.0f;
    double sum = 0, sumSq = 0;
    long count = 0;
    for (int y = 1; y < height - 1; ++y) {
        for (int x = 1; x < width - 1; ++x) {
            const int i = y * width + x;
            const float lap = 4.0f * luma[i]
                - luma[i - 1] - luma[i + 1]
                - luma[i - width] - luma[i + width];
            sum += lap;
            sumSq += static_cast<double>(lap) * lap;
            ++count;
        }
    }
    if (count == 0) return 0.0f;
    const double mean = sum / count;
    return static_cast<float>(std::max(0.0, sumSq / count - mean * mean));
}

}  // namespace

SceneMotionAnalyzer::SceneMotionAnalyzer(AnalyzerConfig cfg) : cfg_(cfg) {}

void SceneMotionAnalyzer::pushFrame(const uint8_t* rgb, int width, int height, double tSec) {
    const int n = width * height;
    std::vector<float> h(n), s(n), v(n), luma(n);

    double sumDh = 0, sumDs = 0, sumDv = 0, sumDl = 0;
    // Hasler-Süsstrunk colorfulness accumulators.
    double sumRg = 0, sumRg2 = 0, sumYb = 0, sumYb2 = 0;
    for (int i = 0; i < n; ++i) {
        const uint8_t r = rgb[i * 3 + 0];
        const uint8_t g = rgb[i * 3 + 1];
        const uint8_t b = rgb[i * 3 + 2];
        rgbToHsv(r, g, b, h[i], s[i], v[i]);
        // Rec.601 luma.
        luma[i] = 0.299f * r + 0.587f * g + 0.114f * b;

        const double rg = static_cast<double>(r) - g;
        const double yb = 0.5 * (static_cast<double>(r) + g) - b;
        sumRg += rg; sumRg2 += rg * rg;
        sumYb += yb; sumYb2 += yb * yb;

        if (hasPrev_) {
            sumDh += std::fabs(h[i] - prevH_[i]);
            sumDs += std::fabs(s[i] - prevS_[i]);
            sumDv += std::fabs(v[i] - prevV_[i]);
            sumDl += std::fabs(luma[i] - prevLuma_[i]);
        }
    }

    float content = 0.0f, motion = 0.0f;
    if (hasPrev_ && n > 0) {
        content = static_cast<float>((sumDh + sumDs + sumDv) / (3.0 * n));  // 0..255
        motion = static_cast<float>((sumDl / n) / 255.0);                   // 0..1
    }

    // Colorfulness: sqrt(var_rg + var_yb) + 0.3*sqrt(mean_rg^2 + mean_yb^2).
    float colorfulness = 0.0f;
    if (n > 0) {
        const double meanRg = sumRg / n, meanYb = sumYb / n;
        const double varRg = std::max(0.0, sumRg2 / n - meanRg * meanRg);
        const double varYb = std::max(0.0, sumYb2 / n - meanYb * meanYb);
        colorfulness = static_cast<float>(
            std::sqrt(varRg + varYb) + 0.3 * std::sqrt(meanRg * meanRg + meanYb * meanYb));
    }

    // Sharpness: variance of the Laplacian over luma (interior pixels).
    float sharpness = computeSharpness(luma, width, height);

    samples_.push_back({tSec, content, motion, sharpness, colorfulness});

    prevH_ = std::move(h);
    prevS_ = std::move(s);
    prevV_ = std::move(v);
    prevLuma_ = std::move(luma);
    hasPrev_ = true;
}

std::vector<double> SceneMotionAnalyzer::cutTimes() const {
    std::vector<double> cuts;
    const int win = cfg_.adaptiveWindow;
    int lastCut = -cfg_.minSceneLenFrames;

    for (int i = 1; i < static_cast<int>(samples_.size()); ++i) {
        const float content = samples_[i].content;

        // Rolling average of neighbouring content scores (excluding i).
        double sum = 0;
        int cnt = 0;
        for (int j = std::max(1, i - win); j <= std::min<int>(samples_.size() - 1, i + win); ++j) {
            if (j == i) continue;
            sum += samples_[j].content;
            ++cnt;
        }
        const float avg = cnt > 0 ? static_cast<float>(sum / cnt) : 0.0f;
        const float ratio = avg > 1e-6f ? content / avg : (content > 0 ? cfg_.adaptiveRatio + 1 : 0);

        const bool adaptiveHit = ratio > cfg_.adaptiveRatio && content > cfg_.adaptiveMinContent;
        const bool fixedHit = content > cfg_.contentThreshold;

        if ((adaptiveHit || fixedHit) && (i - lastCut) >= cfg_.minSceneLenFrames) {
            cuts.push_back(samples_[i].tSec);
            lastCut = i;
        }
    }
    return cuts;
}

}  // namespace ch0p
