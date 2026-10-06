package com.rvmmv.melolab;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Mutable offline projects; timing is in sixteenth-note steps. */
public final class MusicData {
    private MusicData() { }
    public static class Note {
        public int midi = 65, start = 0, length = 2, velocity = 90;
    }
    public static class Chord {
        public int root = 5, octave = 4, inversion = 0, beats = 4;
        public String quality = "minor";
    }
    public static class Project {
        public String id = UUID.randomUUID().toString(), name = "Новая идея", genre = "trap", scaleId = "minor";
        public int root = 5, bpm = 140, seed = 1;
        public boolean playChords = true, playMelody = true, playBass = true;
        public List<Chord> chords = new ArrayList<>();
        public List<Note> melody = new ArrayList<>();
        public long createdAt = System.currentTimeMillis(), updatedAt = createdAt;
    }
    public static class State {
        public List<Project> projects = new ArrayList<>();
        public String currentId = "";
        public boolean flats;
    }

    public static State defaults() {
        State state = new State(); Project project = new Project();
        project.name = "Стартовая идея · F minor";
        MusicTheory.applyProgression(project, "genre"); MusicTheory.generateMelody(project, 1, true);
        state.projects.add(project); state.currentId = project.id; return state;
    }

    public static Project current(State state) {
        if (state == null || state.projects == null || state.currentId == null) return null;
        for (Project project : state.projects) if (project != null && state.currentId.equals(project.id)) return project;
        return null;
    }

    public static Project copy(Project source) {
        if (source == null) return null;
        Project copy = new Project(); copy.id = source.id; copy.name = source.name; copy.genre = source.genre;
        copy.scaleId = source.scaleId; copy.root = source.root; copy.bpm = source.bpm; copy.seed = source.seed;
        copy.playChords = source.playChords; copy.playMelody = source.playMelody; copy.playBass = source.playBass;
        copy.createdAt = source.createdAt; copy.updatedAt = source.updatedAt;
        for (Chord chord : source.chords) {
            Chord item = new Chord(); item.root = chord.root; item.octave = chord.octave; item.inversion = chord.inversion;
            item.beats = chord.beats; item.quality = chord.quality; copy.chords.add(item);
        }
        for (Note note : source.melody) copy.melody.add(copyNote(note));
        return copy;
    }

    static Note copyNote(Note note) {
        Note copy = new Note(); copy.midi = note.midi; copy.start = note.start; copy.length = note.length; copy.velocity = note.velocity;
        return copy;
    }

    public static int totalSteps(Project project) {
        if (project == null || project.chords == null) return 0;
        long total = 0;
        for (Chord chord : project.chords) {
            if (chord == null) throw bad("Пустой аккорд.");
            total += (long) chord.beats * 4;
        }
        if (total < 0 || total > Integer.MAX_VALUE) throw bad("Слишком большая длительность.");
        return (int) total;
    }

    public static void validate(State state) {
        if (state == null || state.projects == null || state.projects.isEmpty() || state.projects.size() > 200) {
            throw bad("Нужно от 1 до 200 проектов.");
        }
        Set<String> ids = new HashSet<>();
        for (Project project : state.projects) { validateProject(project); if (!ids.add(project.id)) throw bad("Повторяется код проекта."); }
        if (current(state) == null) throw bad("Текущий проект не найден.");
    }

    public static void validateProject(Project project) {
        if (project == null) throw bad("Нет проекта.");
        text(project.id, 80, "Код проекта"); text(project.name, 120, "Название проекта");
        if (!isGenre(project.genre)) throw bad("Неизвестный жанр.");
        MusicTheory.scale(project.scaleId); range(project.root, 0, 11, "Тоника"); range(project.bpm, 40, 240, "Темп");
        if (project.createdAt <= 0 || project.updatedAt < project.createdAt || project.updatedAt > 253402300799999L) {
            throw bad("Некорректное время проекта.");
        }
        if (project.chords == null || project.chords.isEmpty() || project.chords.size() > 32) throw bad("Нужно от 1 до 32 аккордов.");
        for (Chord chord : project.chords) {
            if (chord == null) throw bad("Пустой аккорд.");
            range(chord.root, 0, 11, "Корень аккорда"); range(chord.octave, 0, 8, "Октава аккорда");
            range(chord.beats, 1, 16, "Доли аккорда");
            range(chord.inversion, 0, MusicTheory.quality(chord.quality).intervals.length - 1, "Обращение");
            for (int midi : MusicTheory.chordNotes(chord)) range(midi, 0, 127, "Нота аккорда");
        }
        int total = totalSteps(project);
        if (project.melody == null || project.melody.size() > 2048) throw bad("В мелодии допускается до 2048 нот.");
        for (Note note : project.melody) {
            if (note == null) throw bad("Пустая нота.");
            range(note.midi, 0, 127, "Высота ноты"); range(note.velocity, 1, 127, "Громкость ноты");
            range(note.start, 0, total - 1, "Начало ноты"); range(note.length, 1, total, "Длина ноты");
            if ((long) note.start + note.length > total) throw bad("Нота выходит за длительность последовательности.");
        }
    }

    private static boolean isGenre(String id) {
        return "trap".equals(id) || "drill".equals(id) || "boom_bap".equals(id) || "lofi".equals(id)
                || "phonk".equals(id) || "plugg".equals(id) || "west_coast".equals(id);
    }
    private static void text(String value, int max, String label) {
        if (value == null || value.trim().isEmpty() || value.length() > max) throw bad(label + ": неверная строка.");
    }
    private static void range(int value, int min, int max, String label) {
        if (value < min || value > max) throw bad(label + ": значение вне диапазона.");
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}
