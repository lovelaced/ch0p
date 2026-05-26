// Host smoke test for the scene/motion detector. Builds and runs on macOS/Linux with no
// Android toolchain, so algorithm bugs are caught in seconds:
//
//   c++ -std=c++17 -O2 -I../../main/cpp host_test.cpp ../../main/cpp/scene_motion.cpp -o /tmp/ht && /tmp/ht
//
// (The exact command is wrapped in tools/hosttest.sh.)

#include <cmath>
#include <cstdint>
#include <cstdio>
#include <vector>

#include "scene_motion.h"

namespace {

constexpr int W = 32, H = 18;

std::vector<uint8_t> solid(uint8_t r, uint8_t g, uint8_t b) {
    std::vector<uint8_t> f(W * H * 3);
    for (int i = 0; i < W * H; ++i) { f[i * 3] = r; f[i * 3 + 1] = g; f[i * 3 + 2] = b; }
    return f;
}

// High-frequency checkerboard => high Laplacian variance (sharp edges).
std::vector<uint8_t> checker() {
    std::vector<uint8_t> f(W * H * 3);
    for (int y = 0; y < H; ++y)
        for (int x = 0; x < W; ++x) {
            const uint8_t c = ((x + y) % 2 == 0) ? 255 : 0;
            const int i = y * W + x;
            f[i * 3] = c; f[i * 3 + 1] = c; f[i * 3 + 2] = c;
        }
    return f;
}

int failures = 0;
void check(bool ok, const char* msg) {
    std::printf("[%s] %s\n", ok ? "PASS" : "FAIL", msg);
    if (!ok) ++failures;
}

}  // namespace

int main() {
    ch0p::SceneMotionAnalyzer analyzer{ch0p::AnalyzerConfig{}};

    const double fps = 4.0;
    const int total = 40;
    const int cutFrame = 20;  // hard cut blue -> red at t = 5.0s

    for (int i = 0; i < total; ++i) {
        const double t = i / fps;
        std::vector<uint8_t> frame;
        if (i < cutFrame) {
            frame = solid(20, 40, 200);  // steady blue, no motion
        } else {
            // Red half with frame-to-frame brightness oscillation => motion energy.
            const uint8_t r = (i % 2 == 0) ? 220 : 120;
            frame = solid(r, 30, 30);
        }
        analyzer.pushFrame(frame.data(), W, H, t);
    }

    const auto cuts = analyzer.cutTimes();
    std::printf("detected %zu cut(s)\n", cuts.size());
    for (double c : cuts) std::printf("  cut @ %.2fs\n", c);

    bool cutNearFive = false;
    for (double c : cuts) if (std::fabs(c - 5.0) < 0.30) cutNearFive = true;
    check(cuts.size() >= 1, "at least one cut detected");
    check(cutNearFive, "a cut lands near the 5.0s blue->red transition");

    // Motion should be higher in the oscillating red half than the steady blue half.
    const auto& s = analyzer.samples();
    double blueMotion = 0, redMotion = 0;
    int bn = 0, rn = 0;
    for (size_t i = 1; i < s.size(); ++i) {
        if (static_cast<int>(i) < cutFrame) { blueMotion += s[i].motion; ++bn; }
        else if (static_cast<int>(i) > cutFrame) { redMotion += s[i].motion; ++rn; }
    }
    if (bn) blueMotion /= bn;
    if (rn) redMotion /= rn;
    std::printf("mean motion: blue=%.4f red=%.4f\n", blueMotion, redMotion);
    check(redMotion > blueMotion, "oscillating half has higher motion energy");

    // Sharpness + colorfulness (intra-frame; checked on a separate analyzer).
    ch0p::SceneMotionAnalyzer aesthetic{ch0p::AnalyzerConfig{}};
    auto gray = solid(128, 128, 128);
    auto sharp = checker();
    auto red = solid(220, 20, 20);
    aesthetic.pushFrame(gray.data(), W, H, 0.0);   // sample 0: flat gray
    aesthetic.pushFrame(sharp.data(), W, H, 0.25);  // sample 1: edges
    aesthetic.pushFrame(red.data(), W, H, 0.50);    // sample 2: saturated colour
    const auto& a2 = aesthetic.samples();
    std::printf("sharpness: gray=%.1f checker=%.1f\n", a2[0].sharpness, a2[1].sharpness);
    std::printf("colorfulness: gray=%.1f red=%.1f\n", a2[0].colorfulness, a2[2].colorfulness);
    check(a2[1].sharpness > a2[0].sharpness, "checkerboard is sharper than flat gray");
    check(a2[2].colorfulness > a2[0].colorfulness, "saturated red is more colorful than gray");

    std::printf(failures == 0 ? "\nALL HOST TESTS PASSED\n" : "\n%d HOST TEST(S) FAILED\n", failures);
    return failures == 0 ? 0 : 1;
}
