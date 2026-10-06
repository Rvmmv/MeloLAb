package com.rvmmv.melolab;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One atomic local document. Invalid imports and corrupted drafts never overwrite it. */
public final class ProjectStore {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final long MAX_TIMESTAMP = 253402300799999L;
    private static final String KEY = "projects_v1";
    private final SharedPreferences prefs;

    public ProjectStore(Context context) {
        if (context == null) throw bad("Не указан контекст приложения.");
        prefs = context.getApplicationContext().getSharedPreferences("melolab_projects", Context.MODE_PRIVATE);
    }

    public MusicData.State read() {
        String saved = rawJson();
        if (saved == null) return MusicData.defaults();
        try { return importJson(saved); }
        catch (IllegalArgumentException error) {
            throw new IllegalStateException("Сохранённые проекты повреждены. Исходная запись сохранена: экспортируй её или восстанови резервную копию.", error);
        }
    }

    /** Unmodified local document, including corrupted text, for explicit recovery export. */
    public String rawJson() { return prefs.getString(KEY, null); }

    public void save(MusicData.State state) {
        String json = exportJson(state);
        if (!prefs.edit().putString(KEY, json).commit()) {
            throw new IllegalStateException("Не удалось сохранить проекты. Проверь свободное место на телефоне.");
        }
    }

    public String exportJson(MusicData.State state) {
        MusicData.validate(state);
        // Serialize one project at a time: a valid but oversized draft collection
        // must fail at 8 MiB without first duplicating every note into a JSON tree.
        StringBuilder output = new StringBuilder("{\"format\":\"melolab\",\"version\":1,\"state\":{\"currentId\":");
        Json.append(output, state.currentId);
        output.append(",\"flats\":"); Json.append(output, state.flats);
        output.append(",\"projects\":[");
        boolean first = true;
        for (MusicData.Project project : state.projects) {
            if (!first) output.append(','); first = false;
            Json.append(output, writeProject(project));
        }
        output.append("]}}");
        String result = output.toString();
        checkSize(result);
        return result;
    }

    /** Fully detached data; validation succeeds before the caller can elect to save. */
    public MusicData.State importJson(String source) {
        checkSize(source);
        Map<String, Object> document = object(Json.parse(source), "Резервная копия");
        fields(document, "format", "version", "state");
        if (!"melolab".equals(string(document, "format"))) throw bad("Это не резервная копия MeloLab.");
        if (integer(document, "version") != 1) throw bad("Версия резервной копии не поддерживается.");
        Map<String, Object> data = object(document.get("state"), "Данные приложения");
        fields(data, "projects", "currentId", "flats");
        MusicData.State state = new MusicData.State();
        state.currentId = string(data, "currentId"); state.flats = bool(data, "flats");
        List<Object> projects = array(data.get("projects"), 1, 200, "Проекты");
        for (Object item : projects) state.projects.add(readProject(object(item, "Проект")));
        MusicData.validate(state);
        return state;
    }

    private static Map<String, Object> writeProject(MusicData.Project project) {
        Map<String, Object> value = map();
        value.put("id", project.id); value.put("name", project.name); value.put("genre", project.genre);
        value.put("scaleId", project.scaleId); value.put("root", project.root); value.put("bpm", project.bpm);
        value.put("seed", project.seed); value.put("playChords", project.playChords);
        value.put("playMelody", project.playMelody); value.put("playBass", project.playBass);
        value.put("createdAt", project.createdAt); value.put("updatedAt", project.updatedAt);
        List<Object> chords = new ArrayList<>();
        for (MusicData.Chord chord : project.chords) {
            Map<String, Object> item = map();
            item.put("root", chord.root); item.put("quality", chord.quality); item.put("octave", chord.octave);
            item.put("inversion", chord.inversion); item.put("beats", chord.beats); chords.add(item);
        }
        value.put("chords", chords);
        List<Object> notes = new ArrayList<>();
        for (MusicData.Note note : project.melody) {
            Map<String, Object> item = map();
            item.put("midi", note.midi); item.put("start", note.start);
            item.put("length", note.length); item.put("velocity", note.velocity); notes.add(item);
        }
        value.put("melody", notes);
        return value;
    }

    private static MusicData.Project readProject(Map<String, Object> value) {
        fields(value, "id", "name", "genre", "scaleId", "root", "bpm", "seed", "playChords",
                "playMelody", "playBass", "createdAt", "updatedAt", "chords", "melody");
        MusicData.Project project = new MusicData.Project();
        project.id = string(value, "id"); project.name = string(value, "name"); project.genre = string(value, "genre");
        project.scaleId = string(value, "scaleId"); project.root = integer(value, "root");
        project.bpm = integer(value, "bpm"); project.seed = integer(value, "seed");
        project.playChords = bool(value, "playChords"); project.playMelody = bool(value, "playMelody");
        project.playBass = bool(value, "playBass"); project.createdAt = whole(value, "createdAt");
        project.updatedAt = whole(value, "updatedAt");
        for (Object raw : array(value.get("chords"), 1, 32, "Аккорды")) {
            Map<String, Object> item = object(raw, "Аккорд");
            fields(item, "root", "quality", "octave", "inversion", "beats");
            MusicData.Chord chord = new MusicData.Chord();
            chord.root = integer(item, "root"); chord.quality = string(item, "quality");
            chord.octave = integer(item, "octave"); chord.inversion = integer(item, "inversion");
            chord.beats = integer(item, "beats"); project.chords.add(chord);
        }
        for (Object raw : array(value.get("melody"), 0, 2048, "Ноты мелодии")) {
            Map<String, Object> item = object(raw, "Нота"); fields(item, "midi", "start", "length", "velocity");
            MusicData.Note note = new MusicData.Note();
            note.midi = integer(item, "midi"); note.start = integer(item, "start");
            note.length = integer(item, "length"); note.velocity = integer(item, "velocity");
            project.melody.add(note);
        }
        MusicData.validateProject(project);
        return project;
    }

    private static Map<String, Object> map() { return new LinkedHashMap<>(); }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String label) {
        if (!(value instanceof Map)) throw bad(label + ": ожидается объект.");
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> array(Object value, int min, int max, String label) {
        if (!(value instanceof List)) throw bad(label + ": ожидается список.");
        List<Object> result = (List<Object>) value;
        if (result.size() < min || result.size() > max) throw bad(label + ": недопустимое число записей.");
        return result;
    }

    private static void fields(Map<String, Object> value, String... expected) {
        Set<String> keys = new HashSet<>(Arrays.asList(expected));
        if (!keys.equals(value.keySet())) throw bad("Неизвестное или отсутствующее поле резервной копии.");
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof String)) throw bad(key + ": ожидается строка.");
        return (String) item;
    }

    private static boolean bool(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Boolean)) throw bad(key + ": ожидается логическое значение.");
        return (Boolean) item;
    }

    private static int integer(Map<String, Object> value, String key) {
        long number = whole(value, key);
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) throw bad(key + ": целое число слишком большое.");
        return (int) number;
    }

    private static long whole(Map<String, Object> value, String key) {
        Object item = value.get(key);
        if (!(item instanceof Number)) throw bad(key + ": ожидается целое число.");
        if (item instanceof BigDecimal) {
            try {
                BigDecimal decimal = (BigDecimal) item;
                if (decimal.abs().compareTo(BigDecimal.valueOf(MAX_TIMESTAMP)) > 0) throw bad(key + ": целое число слишком большое.");
                decimal = decimal.stripTrailingZeros();
                if (decimal.scale() > 0) throw bad(key + ": некорректное целое число.");
                return decimal.longValueExact();
            } catch (ArithmeticException error) {
                throw bad(key + ": некорректное целое число.");
            }
        }
        long number = ((Number) item).longValue();
        if (number < -MAX_TIMESTAMP || number > MAX_TIMESTAMP) throw bad(key + ": целое число слишком большое.");
        return number;
    }

    private static void checkSize(String source) {
        if (source == null || source.isEmpty()) throw bad("Резервная копия пустая.");
        if (source.length() > MAX_BYTES || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw bad("Резервная копия больше 8 МБ. Проекты не будут обрезаны; операция отменена.");
        }
    }

    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }

    /** Strict, small JSON codec. No lenient coercion, comments, duplicate keys or truncation. */
    private static final class Json {
        private final String source;
        private int position;
        private Json(String source) { this.source = source; }

        static Object parse(String source) {
            Json parser = new Json(source);
            Object value = parser.value(0);
            parser.space();
            if (parser.position != source.length()) throw bad("После JSON-документа есть лишние данные.");
            return value;
        }

        private Object value(int depth) {
            space();
            if (position >= source.length()) throw bad("JSON-документ не завершён.");
            char c = source.charAt(position);
            if (c == '{') return object(depth + 1);
            if (c == '[') return array(depth + 1);
            if (c == '"') return text();
            if (c == 't') { literal("true"); return Boolean.TRUE; }
            if (c == 'f') { literal("false"); return Boolean.FALSE; }
            if (c == 'n') { literal("null"); return null; }
            if (c == '-' || digit(c)) return number();
            throw bad("Неверный синтаксис JSON.");
        }

        private Map<String, Object> object(int depth) {
            if (depth > 32) throw bad("Вложенность резервной копии больше 32 уровней.");
            position++; space();
            Map<String, Object> result = map();
            if (take('}')) return result;
            do {
                space();
                if (position >= source.length() || source.charAt(position) != '"') throw bad("Ключ JSON должен быть строкой.");
                String key = text(); space(); expect(':');
                if (result.containsKey(key)) throw bad("Повторяющийся ключ JSON: " + key);
                if (result.size() >= 32) throw bad("Слишком много полей JSON-объекта.");
                result.put(key, value(depth)); space();
                if (take('}')) return result;
                expect(',');
            } while (true);
        }

        private List<Object> array(int depth) {
            if (depth > 32) throw bad("Вложенность резервной копии больше 32 уровней.");
            position++; space();
            List<Object> result = new ArrayList<>();
            if (take(']')) return result;
            do {
                if (result.size() >= 2048) throw bad("Слишком много элементов JSON-списка.");
                result.add(value(depth)); space();
                if (take(']')) return result;
                expect(',');
            } while (true);
        }

        private String text() {
            expect('"');
            StringBuilder result = new StringBuilder();
            boolean high = false;
            while (position < source.length()) {
                char c = source.charAt(position++);
                if (c == '"') {
                    if (high) throw bad("Непарный символ Unicode в JSON.");
                    return result.toString();
                }
                if (c < 32) throw bad("Управляющий символ в JSON-строке должен быть экранирован.");
                if (c == '\\') {
                    if (position >= source.length()) throw bad("JSON-строка не завершена.");
                    char escape = source.charAt(position++);
                    switch (escape) {
                        case '"': c = '"'; break;
                        case '\\': c = '\\'; break;
                        case '/': c = '/'; break;
                        case 'b': c = '\b'; break;
                        case 'f': c = '\f'; break;
                        case 'n': c = '\n'; break;
                        case 'r': c = '\r'; break;
                        case 't': c = '\t'; break;
                        case 'u':
                            if (position + 4 > source.length()) throw bad("Короткая Unicode-последовательность JSON.");
                            int unit = 0;
                            for (int i = 0; i < 4; i++) {
                                int hex = hex(source.charAt(position++));
                                if (hex < 0) throw bad("Неверная Unicode-последовательность JSON.");
                                unit = unit * 16 + hex;
                            }
                            c = (char) unit; break;
                        default: throw bad("Неверная escape-последовательность JSON.");
                    }
                }
                if (high && !Character.isLowSurrogate(c)) throw bad("Непарный символ Unicode в JSON.");
                if (!high && Character.isLowSurrogate(c)) throw bad("Непарный символ Unicode в JSON.");
                high = Character.isHighSurrogate(c);
                result.append(c);
            }
            throw bad("JSON-строка не завершена.");
        }

        private Number number() {
            int start = position;
            take('-');
            if (take('0')) {
                if (position < source.length() && digit(source.charAt(position))) throw bad("Число JSON начинается с лишнего нуля.");
            } else digits();
            boolean decimal = false;
            if (take('.')) { decimal = true; digits(); }
            if (take('e') || take('E')) { decimal = true; if (!take('+')) take('-'); digits(); }
            if (position - start > 64) throw bad("Число JSON слишком длинное.");
            String encoded = source.substring(start, position);
            try {
                if (!decimal) return Long.valueOf(encoded);
                return new BigDecimal(encoded);
            } catch (NumberFormatException error) { throw bad("Неверное число JSON."); }
        }

        private void digits() {
            int start = position;
            while (position < source.length() && digit(source.charAt(position))) position++;
            if (start == position) throw bad("В числе JSON отсутствуют цифры.");
        }
        private static boolean digit(char c) { return c >= '0' && c <= '9'; }
        private static int hex(char c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return -1;
        }
        private void literal(String expected) {
            if (!source.startsWith(expected, position)) throw bad("Неверное значение JSON.");
            position += expected.length();
        }
        private void space() {
            while (position < source.length()) {
                char c = source.charAt(position);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') break;
                position++;
            }
        }
        private boolean take(char c) {
            if (position < source.length() && source.charAt(position) == c) { position++; return true; }
            return false;
        }
        private void expect(char c) { if (!take(c)) throw bad("Нарушена структура JSON."); }

        static String write(Object value) {
            StringBuilder result = new StringBuilder(); append(result, value); return result.toString();
        }
        private static void append(StringBuilder output, Object value) {
            if (value instanceof Map) {
                output.append('{'); boolean first = true;
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    if (!first) output.append(','); first = false;
                    quote(output, (String) entry.getKey()); output.append(':'); append(output, entry.getValue());
                }
                output.append('}');
            } else if (value instanceof List) {
                output.append('['); boolean first = true;
                for (Object item : (List<?>) value) { if (!first) output.append(','); first = false; append(output, item); }
                output.append(']');
            } else if (value instanceof String) quote(output, (String) value);
            else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) output.append(value);
            else if (value == null) output.append("null");
            else throw bad("Неподдерживаемый тип JSON.");
            if (output.length() > MAX_BYTES) throw bad("Резервная копия больше 8 МБ. Проекты не будут обрезаны.");
        }
        private static void quote(StringBuilder output, String value) {
            output.append('"');
            boolean high = false;
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (high && !Character.isLowSurrogate(c)) throw bad("Непарный символ Unicode в имени проекта.");
                if (!high && Character.isLowSurrogate(c)) throw bad("Непарный символ Unicode в имени проекта.");
                high = Character.isHighSurrogate(c);
                switch (c) {
                    case '"': output.append("\\\""); break;
                    case '\\': output.append("\\\\"); break;
                    case '\b': output.append("\\b"); break;
                    case '\f': output.append("\\f"); break;
                    case '\n': output.append("\\n"); break;
                    case '\r': output.append("\\r"); break;
                    case '\t': output.append("\\t"); break;
                    default:
                        if (c < 32) {
                            output.append("\\u00"); output.append("0123456789abcdef".charAt(c / 16));
                            output.append("0123456789abcdef".charAt(c % 16));
                        } else output.append(c);
                }
            }
            if (high) throw bad("Непарный символ Unicode в имени проекта.");
            output.append('"');
        }
    }
}
