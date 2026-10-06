package com.rvmmv.melolab;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Actual JSON, validation and storage behavior; no Android disk/runtime claim. */
public final class ProjectStoreTests {
    private static int assertions, cases;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    public static void main(String[] args) {
        MemoryContext context = new MemoryContext();
        ProjectStore store = new ProjectStore(context);
        check(store.rawJson() == null, "absent storage preserved");
        MusicData.State source = store.read();
        MusicData.validate(source);
        check(source.projects.size() == 1, "defaults provided only when absent");
        check(store.rawJson() == null, "read default does not write"); cases++;

        source.flats = true;
        MusicData.Project project = MusicData.current(source);
        project.id = "original"; source.currentId = project.id;
        project.name = "Идея 𝄞 \"F minor\" \\\nтест";
        project.seed = Integer.MIN_VALUE; project.playBass = false; project.playMelody = false;
        project.melody.clear();
        project.melody.add(note(0, 0, 4, 1)); project.melody.add(note(127, 0, 8, 127));
        String encoded = store.exportJson(source);
        MusicData.State detached = store.importJson(encoded);
        check(encoded.equals(store.exportJson(detached)), "complete canonical roundtrip");
        check(MusicData.current(detached).melody.size() == 2, "polyphony and out-of-scale notes preserved");
        check(detached != source && detached.projects != source.projects && MusicData.current(detached).chords != project.chords,
                "detached objects/lists");
        check(MusicData.current(detached).seed == Integer.MIN_VALUE, "full int seed preserved");
        check(store.rawJson() == null, "import never saves");
        store.save(source); String before = store.rawJson();
        check(before.equals(encoded), "save uses full versioned document");
        MusicData.current(detached).name = "Changed detached";
        check(store.rawJson().equals(before) && project.name.contains("F minor"), "mutating import cannot modify source/store");
        MusicData.State read = store.read(); MusicData.current(read).melody.get(0).midi = 50;
        check(MusicData.current(store.read()).melody.get(0).midi == 0, "reads are independent"); cases++;

        MusicData.State baseState = MusicData.defaults();
        MusicData.current(baseState).id = "base"; baseState.currentId = "base";
        String base = store.exportJson(baseState);
        reject(store, "", "empty"); reject(store, null, "null");
        reject(store, base.replace("\"melolab\"", "\"other\""), "wrong format");
        reject(store, base.replace("\"version\":1", "\"version\":2"), "unsupported version");
        reject(store, base.replace("\"bpm\":140", "\"bpm\":\"140\""), "number string");
        reject(store, base.replace("\"playBass\":true", "\"playBass\":\"false\""), "boolean string");
        reject(store, base.replace("\"seed\":1", "\"seed\":1e-999"), "underflow cannot become integer zero");
        reject(store, base.replace("\"bpm\":140", "\"bpm\":140.000000000000000000001"), "fraction cannot round to integer");
        reject(store, base.replace("\"seed\":1", "\"seed\":1e2147483647"), "huge exponent");
        reject(store, base.replace("\"seed\":1", "\"seed\":100e2147483647"), "huge exponent with trailing zeros");
        reject(store, base.replace("\"seed\":1", "\"seed\":9223372036854775808"), "long overflow");
        reject(store, base.replace("\"playBass\":true,", ""), "missing field");
        reject(store, base.replace("\"flats\":false", "\"flats\":false,\"extra\":1"), "unknown field");
        reject(store, base.replace("\"currentId\":\"base\"", "\"currentId\":null"), "null reference");
        reject(store, base.replace("\"currentId\":\"base\"", "\"currentId\":\"missing\""), "orphan current reference");
        check(store.exportJson(store.importJson(base.replace("\"bpm\":140", "\"bpm\":1.40e2"))).equals(base),
                "exact integral decimal accepted"); cases++;

        reject(store, base.replace("\"version\":1", "\"version\":1,\"version\":1"), "duplicate key");
        reject(store, base.replace("\"version\":1", "\"version\":1,\"ver\\u0073ion\":1"), "duplicate decoded key");
        reject(store, "/*comment*/" + base, "comments");
        reject(store, base.replace('"', '\''), "single quotes");
        reject(store, base + "{}", "trailing document");
        reject(store, base.substring(0, base.length() - 1) + ",}", "trailing comma");
        reject(store, base.replace("\"seed\":1", "\"seed\":01"), "leading zero");
        reject(store, base.replace("\"seed\":1", "\"seed\":+1"), "leading plus");
        reject(store, base.replace("\"seed\":1", "\"seed\":NaN"), "NaN");
        reject(store, "[".repeat(33) + "0" + "]".repeat(33), "depth above32");
        reject(store, base.replace("\"name\":", "\"name\":\"bad\\ud800\",\"ignored\":"), "unpaired escaped surrogate");
        reject(store, base.replace("\"name\":", "\"name\":\"bad" + '\ud800' + "\",\"ignored\":"), "unpaired raw surrogate");
        reject(store, base.replace("\"name\":", "\"name\":\"bad\\u１２３４\",\"ignored\":"), "non-ASCII escape digits");
        reject(store, base.replace("\"name\":", "\"name\":\"bad" + '\u0001' + "\",\"ignored\":"), "unescaped control"); cases++;

        MusicData.State special = MusicData.defaults();
        MusicData.current(special).name = "Draft \u0000\b\f\n\r\t\"\\𝄞";
        check(store.exportJson(store.importJson(store.exportJson(special))).equals(store.exportJson(special)), "escapes/Unicode roundtrip"); cases++;

        MusicData.State duplicate = MusicData.defaults(); MusicData.Project copy = MusicData.copy(MusicData.current(duplicate));
        copy.id = "copy2"; duplicate.projects.add(copy);
        String duplicateJson = store.exportJson(duplicate);
        reject(store, duplicateJson.replace("\"id\":\"copy2\"", "\"id\":\"" + duplicate.currentId + "\""), "duplicate project IDs");
        reject(store, base.replace("\"root\":5", "\"root\":12"), "pitch-class bound");
        reject(store, base.replace("\"bpm\":140", "\"bpm\":241"), "tempo bound");
        reject(store, base.replace("\"scaleId\":\"minor\"", "\"scaleId\":\"unknown\""), "scale reference");
        reject(store, base.replace("\"quality\":\"minor\"", "\"quality\":\"unknown\""), "quality reference");
        reject(store, base.replace("\"octave\":4", "\"octave\":9"), "chord octave bound"); cases++;

        MusicData.State maximum = new MusicData.State();
        for (int i = 0; i < 200; i++) {
            MusicData.Project item = MusicData.copy(MusicData.current(baseState)); item.id = "p" + i; maximum.projects.add(item);
        }
        maximum.currentId = "p199";
        check(store.importJson(store.exportJson(maximum)).projects.size() == 200, "200 projects preserved");
        MusicData.Project extra = MusicData.copy(maximum.projects.get(0)); extra.id = "p200"; maximum.projects.add(extra);
        illegal(() -> store.exportJson(maximum), "project limit export"); cases++;

        MusicData.State dense = MusicData.defaults(); MusicData.Project denseProject = MusicData.current(dense);
        denseProject.chords.clear();
        for (int i = 0; i < 32; i++) { MusicData.Chord chord = new MusicData.Chord(); chord.beats = 16; denseProject.chords.add(chord); }
        denseProject.melody.clear();
        for (int i = 0; i < 2048; i++) denseProject.melody.add(note(i % 128, i, 1, 100));
        MusicData.State denseRead = store.importJson(store.exportJson(dense));
        check(MusicData.current(denseRead).chords.size() == 32 && MusicData.current(denseRead).melody.size() == 2048,
                "maximum chord/note counts preserved");
        denseProject.melody.add(note(65, 0, 1, 90));
        illegal(() -> store.exportJson(dense), "note limit export"); cases++;

        int padding = MAX_BYTES - base.getBytes(StandardCharsets.UTF_8).length;
        check(store.importJson(base + " ".repeat(padding)).projects.size() == 1, "exact8MiB accepted");
        reject(store, base + " ".repeat(padding + 1), "over8MiB");
        reject(store, "\"" + "я".repeat(MAX_BYTES / 2) + "\"", "UTF8 byte limit despite lower char count"); cases++;

        String persisted = store.rawJson();
        context.preferences.data.put("projects_v1", "{broken");
        failure(store::read, "corrupt read throws");
        check(store.rawJson().equals("{broken"), "corrupt raw preserved for export");
        reject(store, "{broken", "failed import preserves corrupt raw");
        check(store.rawJson().equals("{broken"), "no default replacement after corruption");
        store.save(baseState); check(store.rawJson().equals(base), "explicit recovery save");
        context.preferences.fail = true;
        failure(() -> store.save(source), "commit failure surfaces");
        context.preferences.fail = false;
        check(store.rawJson().equals(base), "failed persistent replacement preserves committed document in storage double");
        MusicData.current(source).bpm = 300;
        illegal(() -> store.save(source), "invalid save rejected before commit");
        check(store.rawJson().equals(base), "invalid save preserves prior document");
        MusicData.current(source).bpm = 140; cases++;

        Random random = new Random(4471);
        for (int sample = 0; sample < 250; sample++) {
            MusicData.State state = new MusicData.State(); state.flats = random.nextBoolean();
            int count = random.nextInt(4) + 1;
            for (int j = 0; j < count; j++) {
                MusicData.Project item = new MusicData.Project(); item.id = "s" + sample + "p" + j;
                item.name = "Идея " + sample + " \"🟣\""; item.seed = random.nextInt(); item.root = random.nextInt(12);
                item.bpm = 40 + random.nextInt(201);
                item.scaleId = MusicTheory.scales().get(random.nextInt(MusicTheory.scales().size())).id;
                item.playBass = random.nextBoolean(); item.playChords = random.nextBoolean(); item.playMelody = random.nextBoolean();
                for (int c = 0, n = 1 + random.nextInt(8); c < n; c++) {
                    MusicData.Chord chord = new MusicData.Chord(); chord.root = random.nextInt(12); chord.octave = random.nextInt(9);
                    MusicTheory.Quality quality = MusicTheory.qualities().get(random.nextInt(MusicTheory.qualities().size()));
                    chord.quality = quality.id; chord.inversion = random.nextInt(quality.intervals.length); chord.beats = 1 + random.nextInt(16);
                    item.chords.add(chord);
                }
                int total = MusicData.totalSteps(item);
                for (int n = 0, limit = random.nextInt(80); n < limit; n++) {
                    int start = random.nextInt(total); item.melody.add(note(random.nextInt(128), start,
                            1 + random.nextInt(total - start), 1 + random.nextInt(127)));
                }
                state.projects.add(item);
            }
            state.currentId = state.projects.get(random.nextInt(count)).id;
            String json = store.exportJson(state);
            check(store.exportJson(store.importJson(json)).equals(json), "random full roundtrip");
        }
        cases++;
        System.out.println("Project store tests: " + cases + " cases, " + assertions + " assertions passed.");
    }

    private static MusicData.Note note(int midi, int start, int length, int velocity) {
        MusicData.Note result = new MusicData.Note(); result.midi = midi; result.start = start;
        result.length = length; result.velocity = velocity; return result;
    }
    private static void reject(ProjectStore store, String json, String label) {
        String before = store.rawJson(); illegal(() -> store.importJson(json), label);
        check(java.util.Objects.equals(before, store.rawJson()), label + " must not write");
    }
    private static void illegal(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label + " accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void failure(Runnable action, String label) {
        try { action.run(); throw new AssertionError(label + " did not fail"); }
        catch (IllegalStateException expected) { assertions++; }
    }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }

    private static final class MemoryContext extends Context {
        final MemoryPreferences preferences = new MemoryPreferences();
        @Override public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
    }
    private static final class MemoryPreferences implements SharedPreferences {
        final Map<String, String> data = new HashMap<>(); boolean fail;
        @Override public String getString(String key, String fallback) { return data.containsKey(key) ? data.get(key) : fallback; }
        @Override public Editor edit() {
            return new Editor() {
                final Map<String, String> pending = new HashMap<>();
                @Override public Editor putString(String key, String value) { pending.put(key, value); return this; }
                @Override public boolean commit() { if (fail) return false; data.putAll(pending); return true; }
            };
        }
    }
}
