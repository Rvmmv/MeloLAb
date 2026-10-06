package com.rvmmv.melolab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Assertions against real generated PCM, independent of Android and sound hardware. */
public final class SynthCoreTest {
    private static int assertions;

    public static void main(String[] args) {
        frequencyAndPitch();
        toneAndEnvelope();
        normalizedPolyphony();
        streamingAndOverlap();
        velocityAndLimits();
        System.out.println("SynthCore: " + assertions + " assertions passed (real PCM; no device audio test).");
    }

    private static void frequencyAndPitch() {
        close(SynthCore.frequency(69), 440, 1e-9, "A frequency");
        close(SynthCore.frequency(60), 261.625565, 0.000001, "Middle C frequency");
        close(SynthCore.frequency(81), 880, 1e-9, "Octave frequency");
        close(SynthCore.frequency(0), 8.1757989, 0.000001, "Lowest MIDI frequency");
        short[] pcm = render(Arrays.asList(event(69, 0, SynthCore.SAMPLE_RATE, 127, true)), 1, 251);
        int first = -1, last = -1, crossings = 0;
        for (int i = SynthCore.SAMPLE_RATE / 5; i < SynthCore.SAMPLE_RATE * 4 / 5; i++) {
            if (pcm[i - 1] <= 0 && pcm[i] > 0) {
                if (first < 0) first = i;
                last = i;
                crossings++;
            }
        }
        check(crossings > 200, "Bass must contain audible cycles");
        close(SynthCore.SAMPLE_RATE * (crossings - 1.0) / (last - first), 440, 0.03, "Generated bass pitch");
    }

    private static void toneAndEnvelope() {
        short[] bass = render(Arrays.asList(event(69, 0, SynthCore.SAMPLE_RATE, 127, true)), 0, 512);
        short[] harmonic = render(Arrays.asList(event(69, 0, SynthCore.SAMPLE_RATE, 127, false)), 0, 512);
        check(bass[0] == 0 && harmonic[0] == 0, "Attack begins at silence");
        check(Math.abs(bass[bass.length - 1]) < 10 && Math.abs(harmonic[harmonic.length - 1]) < 10,
                "Release reaches silence smoothly");
        double bassFundamental = spectralAmplitude(bass, 440);
        double bassSecond = spectralAmplitude(bass, 880);
        double harmonicFundamental = spectralAmplitude(harmonic, 440);
        double harmonicSecond = spectralAmplitude(harmonic, 880);
        check(bassFundamental > 1000, "Sine preview has energy");
        check(bassSecond / bassFundamental < 0.001, "Bass is a sine, not the harmonic voice");
        close(harmonicSecond / harmonicFundamental, 0.18 / 0.65, 0.001, "Harmonic preview actually adds overtone");
    }

    private static void normalizedPolyphony() {
        List<SynthCore.Event> one = Arrays.asList(event(65, 0, 12_000, 127, false));
        List<SynthCore.Event> many = new ArrayList<>();
        for (int i = 0; i < 128; i++) many.add(event(65, 0, 12_000, 127, false));
        short[] solo = render(one, 0, 1024);
        short[] mixed = render(many, 0, 1024);
        check(solo.length == mixed.length, "Polyphony preserves duration");
        int difference = 0, peak = 0;
        for (int i = 0; i < mixed.length; i++) {
            difference = Math.max(difference, Math.abs(mixed[i] - solo[i]));
            peak = Math.max(peak, Math.abs((int) mixed[i]));
        }
        check(difference <= 1, "128 aligned voices are normalized without clipping or gain pumping");
        check(peak > 1000 && peak < 25_600, "PCM is audible and keeps headroom");
    }

    private static void streamingAndOverlap() {
        List<SynthCore.Event> notes = Arrays.asList(event(60, 100, 900, 105, false),
                event(64, 250, 1200, 85, false), event(36, 1300, 700, 110, true));
        short[] tiny = render(notes, 2700, 7);
        short[] large = render(notes, 2700, 4096);
        check(Arrays.equals(tiny, large), "Streaming block partition cannot alter PCM");
        for (int i = 0; i < 100; i++) check(tiny[i] == 0, "Scheduled onset preserves initial rest");
        check(rms(tiny, 300, 900) > 100, "Overlapping chord voices are audible");
        check(rms(tiny, 1400, 1900) > 100, "Later bass event starts independently");
        SynthCore.Renderer renderer = new SynthCore.Renderer(SynthCore.SAMPLE_RATE, notes, 2700);
        short[] guarded = new short[15];
        Arrays.fill(guarded, (short) 1234);
        check(renderer.fill(guarded, 4, 7) == 7, "fill returns only requested frame count");
        check(guarded[3] == 1234 && guarded[11] == 1234, "fill respects buffer offset and bounds");
    }

    private static void velocityAndLimits() {
        short[] loud = render(Arrays.asList(event(69, 0, 30_000, 127, true)), 0, 257);
        short[] quiet = render(Arrays.asList(event(69, 0, 30_000, 32, true)), 0, 257);
        close(rms(quiet, 9000, 20_000) / rms(loud, 9000, 20_000), 32.0 / 127.0, 0.001, "MIDI velocity affects PCM");
        rejects(() -> SynthCore.frequency(-1), "Negative MIDI rejected");
        rejects(() -> event(128, 0, 10, 90, false), "High MIDI rejected");
        rejects(() -> event(60, 0, 0, 90, false), "Empty gate rejected");
        rejects(() -> new SynthCore.Renderer(SynthCore.SAMPLE_RATE,
                Arrays.asList(event(60, 0, SynthCore.SAMPLE_RATE * SynthCore.MAX_SECONDS, 90, false)), 0),
                "Five-minute cap includes release tail without allocating full PCM");
        SynthCore.Renderer silent = new SynthCore.Renderer(SynthCore.SAMPLE_RATE,
                Arrays.asList(event(60, 0, 100, 0, false)), 150);
        check(!silent.hasSound(), "Muted note does not create an audible voice");
        short[] block = new short[64];
        int total = 0, count;
        while ((count = silent.fill(block, 0, block.length)) != 0) {
            for (int i = 0; i < count; i++) check(block[i] == 0, "Silence renders zeros");
            total += count;
        }
        check(total == 150 && silent.fill(block, 0, 64) == 0, "Renderer completes exactly once");
    }

    private static SynthCore.Event event(int midi, int start, int frames, int velocity, boolean bass) {
        return new SynthCore.Event(midi, start, frames, velocity, bass);
    }

    private static short[] render(List<SynthCore.Event> events, int minimumFrames, int blockSize) {
        SynthCore.Renderer renderer = new SynthCore.Renderer(SynthCore.SAMPLE_RATE, events, minimumFrames);
        short[] result = new short[renderer.totalFrames()];
        short[] block = new short[blockSize];
        int offset = 0, count;
        while ((count = renderer.fill(block, 0, block.length)) > 0) {
            System.arraycopy(block, 0, result, offset, count);
            offset += count;
        }
        check(offset == result.length, "Rendering completes at declared frame count");
        return result;
    }

    private static double spectralAmplitude(short[] pcm, double frequency) {
        int start = SynthCore.SAMPLE_RATE / 5, length = SynthCore.SAMPLE_RATE / 2;
        double sine = 0, cosine = 0;
        for (int i = start; i < start + length; i++) {
            double phase = 2.0 * Math.PI * frequency * i / SynthCore.SAMPLE_RATE;
            sine += pcm[i] * Math.sin(phase);
            cosine += pcm[i] * Math.cos(phase);
        }
        return 2.0 * Math.hypot(sine, cosine) / length;
    }

    private static double rms(short[] pcm, int start, int end) {
        double sum = 0;
        for (int i = start; i < end; i++) sum += (double) pcm[i] * pcm[i];
        return Math.sqrt(sum / (end - start));
    }

    private static void rejects(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }

    private static void close(double actual, double expected, double tolerance, String message) {
        check(Math.abs(actual - expected) <= tolerance, message + ": " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
