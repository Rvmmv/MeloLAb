package com.rvmmv.melolab;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground synthetic audition. The Activity stops this player when it pauses. */
public final class SynthPlayer {
    private static final int BLOCK_FRAMES = 1024;
    private final Object lock = new Object();
    private final AudioManager manager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "MeloLab-preview");
        thread.setDaemon(true);
        return thread;
    });
    private final AudioAttributes attributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
    private long generation;
    private boolean playing;
    private boolean released;
    private AudioTrack currentTrack;
    private AudioFocusRequest focusRequest;

    public SynthPlayer(Context context) {
        if (context == null) throw new IllegalArgumentException("Не указан контекст звука");
        manager = (AudioManager) context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public void play(MusicData.Project project, Runnable onFinished) {
        MusicData.validateProject(project);
        MusicData.Project copy = MusicData.copy(project);
        double stepFrames = SynthCore.SAMPLE_RATE * 60.0 / (copy.bpm * 4.0);
        long musicalFrames = Math.round(MusicData.totalSteps(copy) * stepFrames);
        if (musicalFrames > (long) SynthCore.SAMPLE_RATE * SynthCore.MAX_SECONDS) throw SynthCore.tooLong();
        List<SynthCore.Event> events = new ArrayList<>();
        for (MusicData.Note note : MusicTheory.playbackNotes(copy)) {
            long start = Math.round(note.start * stepFrames);
            long end = Math.round(((long) note.start + note.length) * stepFrames);
            if (start > Integer.MAX_VALUE || end > Integer.MAX_VALUE || end <= start) {
                throw new IllegalArgumentException("Некорректное время ноты");
            }
            events.add(new SynthCore.Event(note.midi, (int) start, (int) (end - start), note.velocity, note.midi < 48));
        }
        start(new SynthCore.Renderer(SynthCore.SAMPLE_RATE, events, (int) musicalFrames), onFinished);
    }

    public void playNotes(int[] midi, int durationMillis) {
        if (midi == null || midi.length == 0 || midi.length > 128 || durationMillis < 20 || durationMillis > 5000) {
            throw new IllegalArgumentException("Для прослушивания нужны ноты и длительность от 20 до 5000 мс");
        }
        int frames = (int) Math.round(durationMillis * SynthCore.SAMPLE_RATE / 1000.0);
        List<SynthCore.Event> events = new ArrayList<>();
        for (int note : midi) events.add(new SynthCore.Event(note, 0, frames, 100, note < 48));
        start(new SynthCore.Renderer(SynthCore.SAMPLE_RATE, events, frames), null);
    }

    private void start(SynthCore.Renderer renderer, Runnable onFinished) {
        final long token;
        synchronized (lock) {
            if (released) throw new IllegalStateException("Звуковой проигрыватель закрыт");
            token = ++generation;
            playing = false;
            haltTrackLocked();
            abandonFocusLocked();
            if (!renderer.hasSound()) {
                postFinished(token, onFinished);
                return;
            }
            if (manager == null) throw new IllegalStateException("Аудиовыход недоступен");
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(change -> {
                        if (change != AudioManager.AUDIOFOCUS_GAIN) focusLost(token, onFinished);
                    }, main).build();
            if (manager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                abandonFocusLocked();
                throw new IllegalStateException("Звук сейчас занят другим приложением. Повтори прослушивание позже.");
            }
            playing = true;
            worker.execute(() -> stream(token, renderer, onFinished));
        }
    }

    public boolean isPlaying() { synchronized (lock) { return playing; } }

    public void stop() {
        synchronized (lock) {
            generation++;
            playing = false;
            haltTrackLocked();
            abandonFocusLocked();
        }
    }

    public void release() {
        synchronized (lock) {
            if (released) return;
            released = true;
            generation++;
            playing = false;
            haltTrackLocked();
            abandonFocusLocked();
            worker.shutdown();
        }
    }

    private void focusLost(long token, Runnable onFinished) {
        synchronized (lock) {
            if (released || generation != token) return;
            stop();
            // An automatic focus stop also clears the Activity's playing UI. This
            // completion is invalidated if a newer preview starts before delivery.
            postFinished(generation, onFinished);
        }
    }

    private boolean current(long token) {
        synchronized (lock) { return !released && playing && generation == token; }
    }

    private void haltTrackLocked() {
        if (currentTrack == null) return;
        try { currentTrack.pause(); } catch (IllegalStateException ignored) { }
        try { currentTrack.flush(); } catch (IllegalStateException ignored) { }
        // The single worker owns release; nonblocking writes never strand it here.
    }

    private void abandonFocusLocked() {
        if (manager != null && focusRequest != null) {
            manager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        }
    }

    private void stream(long token, SynthCore.Renderer renderer, Runnable onFinished) {
        AudioTrack track = null;
        boolean finished = false;
        try {
            if (!current(token)) return;
            int minimum = AudioTrack.getMinBufferSize(SynthCore.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum < 1) throw new IllegalStateException("AudioTrack does not support the preview format");
            int bufferBytes = Math.max(minimum, BLOCK_FRAMES * 2 * 4);
            track = new AudioTrack.Builder().setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SynthCore.SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(bufferBytes).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IllegalStateException("AudioTrack initialization failed");
            synchronized (lock) {
                if (!current(token)) return;
                currentTrack = track;
                track.play();
            }
            short[] block = new short[BLOCK_FRAMES];
            int frames, writtenTotal = 0;
            while (current(token) && (frames = renderer.fill(block, 0, block.length)) > 0) {
                int offset = 0;
                while (offset < frames && current(token)) {
                    int written = track.write(block, offset, frames - offset, AudioTrack.WRITE_NON_BLOCKING);
                    if (written < 0) throw new IllegalStateException("AudioTrack write failed: " + written);
                    if (written == 0) Thread.sleep(5);
                    else { offset += written; writtenTotal += written; }
                }
            }
            // The final write queues audio rather than playing it immediately. Wait
            // for the small output buffer, keeping cancellation responsive.
            long drainDeadline = SystemClock.uptimeMillis() + 1500 + bufferBytes * 1000L / (SynthCore.SAMPLE_RATE * 2L);
            while (current(token) && Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < writtenTotal
                    && SystemClock.uptimeMillis() < drainDeadline) Thread.sleep(10);
            finished = current(token);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException error) {
            Log.w("MeloLab", "Synthetic preview could not play", error);
            finished = current(token);
        } finally {
            synchronized (lock) {
                if (track != null) {
                    try { track.stop(); } catch (IllegalStateException ignored) { }
                    track.release();
                    if (currentTrack == track) currentTrack = null;
                }
                if (generation == token) {
                    playing = false;
                    abandonFocusLocked();
                }
            }
            if (finished) postFinished(token, onFinished);
        }
    }

    private void postFinished(long token, Runnable onFinished) {
        if (onFinished == null) return;
        main.post(() -> {
            synchronized (lock) {
                if (released || generation != token || playing) return;
                onFinished.run();
            }
        });
    }
}
