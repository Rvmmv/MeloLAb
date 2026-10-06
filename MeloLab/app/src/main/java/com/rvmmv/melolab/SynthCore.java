package com.rvmmv.melolab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Small deterministic PCM synthesizer. This is a preview tone, not piano or an 808 sample. */
public final class SynthCore {
    public static final int SAMPLE_RATE = 44_100;
    public static final int MAX_SECONDS = 300;
    public static final int MAX_EVENTS = 4096;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double HEADROOM = 0.78;
    private static final double[] HARMONICS = { 0.65, 0.18, 0.10, 0.05, 0.02 };

    private SynthCore() { }

    public static double frequency(int midi) {
        if (midi < 0 || midi > 127) throw new IllegalArgumentException("MIDI-нота должна быть от 0 до 127");
        return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    public static final class Event {
        public final int midi, startFrame, gateFrames, velocity;
        public final boolean bass;

        public Event(int midi, int startFrame, int gateFrames, int velocity, boolean bass) {
            frequency(midi);
            if (startFrame < 0 || gateFrames < 1 || velocity < 0 || velocity > 127) {
                throw new IllegalArgumentException("Некорректная нота для прослушивания");
            }
            this.midi = midi;
            this.startFrame = startFrame;
            this.gateFrames = gateFrames;
            this.velocity = velocity;
            this.bass = bass;
        }
    }

    /** A renderer owns only event/voice state. Call fill repeatedly with a small reusable buffer. */
    public static final class Renderer {
        private final int sampleRate;
        private final int totalFrames;
        private final List<Event> events;
        private final List<Voice> active = new ArrayList<>();
        private final double gain;
        private int frame;
        private int nextEvent;

        public Renderer(int sampleRate, List<Event> source, int musicalFrames) {
            if (sampleRate < 8_000 || sampleRate > 192_000 || source == null
                    || source.size() > MAX_EVENTS || musicalFrames < 0) {
                throw new IllegalArgumentException("Некорректные параметры прослушивания");
            }
            long limit = (long) sampleRate * MAX_SECONDS;
            long end = musicalFrames;
            List<Event> copied = new ArrayList<>();
            List<Boundary> boundaries = new ArrayList<>();
            for (Event event : source) {
                if (event == null) throw new IllegalArgumentException("Пустая нота");
                if (event.velocity == 0) continue;
                long releaseEnd = (long) event.startFrame + event.gateFrames + releaseFrames(sampleRate, event.bass);
                if (releaseEnd > limit) throw tooLong();
                end = Math.max(end, releaseEnd);
                copied.add(event);
                double weight = event.velocity / 127.0;
                boundaries.add(new Boundary(event.startFrame, weight));
                boundaries.add(new Boundary((int) releaseEnd, -weight));
            }
            if (end > limit) throw tooLong();
            copied.sort(Comparator.comparingInt(event -> event.startFrame));
            // End before start at a shared boundary. Fixed gain avoids audible pumping
            // and mathematically keeps even aligned, duplicate voices below clipping.
            boundaries.sort((a, b) -> {
                int byFrame = Integer.compare(a.frame, b.frame);
                return byFrame != 0 ? byFrame : Double.compare(a.weight, b.weight);
            });
            double weight = 0.0, maximum = 1.0;
            for (Boundary boundary : boundaries) {
                weight += boundary.weight;
                maximum = Math.max(maximum, weight);
            }
            this.sampleRate = sampleRate;
            this.events = Collections.unmodifiableList(copied);
            this.totalFrames = (int) end;
            this.gain = HEADROOM / maximum;
        }

        public int totalFrames() { return totalFrames; }
        public int renderedFrames() { return frame; }
        public boolean hasSound() { return !events.isEmpty(); }

        /** Returns frames written, or zero at the end; output outside this range is untouched. */
        public int fill(short[] output, int offset, int count) {
            if (output == null || offset < 0 || count < 0 || offset > output.length - count) {
                throw new IllegalArgumentException("Некорректный звуковой буфер");
            }
            int length = Math.min(count, totalFrames - frame);
            for (int i = 0; i < length; i++, frame++) {
                while (nextEvent < events.size() && events.get(nextEvent).startFrame <= frame) {
                    active.add(new Voice(events.get(nextEvent++), sampleRate));
                }
                double sample = 0.0;
                for (int v = active.size() - 1; v >= 0; v--) {
                    Voice voice = active.get(v);
                    int age = frame - voice.event.startFrame;
                    if (age >= voice.totalFrames) {
                        active.remove(v);
                    } else {
                        sample += voice.sample(age);
                    }
                }
                // The bound follows from gain/envelope/harmonic normalization. Clamp
                // only protects the PCM conversion from floating-point roundoff.
                double normalized = Math.max(-1.0, Math.min(1.0, sample * gain));
                output[offset + i] = (short) Math.round(normalized * 32767.0);
            }
            return length;
        }
    }

    public static IllegalArgumentException tooLong() {
        return new IllegalArgumentException("Прослушивание ограничено 5 минутами. Сократи последовательность или повысь BPM; MIDI можно экспортировать полностью.");
    }

    private static int releaseFrames(int sampleRate, boolean bass) {
        return Math.max(1, (int) Math.round(sampleRate * (bass ? 0.12 : 0.09)));
    }

    private static final class Boundary {
        final int frame;
        final double weight;
        Boundary(int frame, double weight) { this.frame = frame; this.weight = weight; }
    }

    private static final class Voice {
        final Event event;
        final int totalFrames, attackFrames, decayFrames, releaseFrames, harmonicCount;
        final double increment, amplitude, harmonicWeight, releaseLevel;

        Voice(Event event, int sampleRate) {
            this.event = event;
            releaseFrames = releaseFrames(sampleRate, event.bass);
            totalFrames = event.gateFrames + releaseFrames;
            attackFrames = Math.max(1, Math.min((int) (sampleRate * 0.006), event.gateFrames / 4));
            decayFrames = Math.max(1, (int) (sampleRate * 0.10));
            double frequency = frequency(event.midi);
            increment = TWO_PI * frequency / sampleRate;
            amplitude = event.velocity / 127.0;
            int harmonics = 1;
            if (!event.bass) {
                // Do not alias high MIDI notes into unrelated lower frequencies.
                while (harmonics < HARMONICS.length && (harmonics + 1) * frequency < sampleRate / 2.0) harmonics++;
            }
            harmonicCount = harmonics;
            double weight = 0.0;
            for (int h = 0; h < harmonicCount; h++) weight += HARMONICS[h];
            harmonicWeight = weight;
            releaseLevel = gatedEnvelope(event.gateFrames);
        }

        double gatedEnvelope(int age) {
            if (age < attackFrames) return age / (double) attackFrames;
            double decay = Math.min(1.0, (age - attackFrames) / (double) decayFrames);
            return 1.0 - 0.42 * decay;
        }

        double sample(int age) {
            double envelope = age < event.gateFrames ? gatedEnvelope(age)
                    : releaseLevel * (1.0 - (age - event.gateFrames) / (double) releaseFrames);
            double phase = increment * age;
            double tone;
            if (event.bass) {
                tone = Math.sin(phase);
            } else {
                tone = 0.0;
                for (int h = 0; h < harmonicCount; h++) tone += HARMONICS[h] * Math.sin((h + 1) * phase);
                tone /= harmonicWeight;
            }
            return tone * envelope * amplitude;
        }
    }
}
