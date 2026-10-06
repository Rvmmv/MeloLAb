package com.rvmmv.melolab;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Interval arithmetic and original seeded composition rules, without a model or network. */
public final class MusicTheory {
    private MusicTheory() { }
    public static final class Scale {
        public final String id, name, description;
        public final int[] intervals;
        public Scale(String id, String name, String description, int[] intervals) {
            this.id = id; this.name = name; this.description = description; this.intervals = intervals.clone();
        }
    }
    public static final class Quality {
        public final String id, name, symbol;
        public final int[] intervals;
        public Quality(String id, String name, String symbol, int[] intervals) {
            this.id = id; this.name = name; this.symbol = symbol; this.intervals = intervals.clone();
        }
    }
    private static final Scale[] SCALES = {
            s("major", "Мажор", "Светлая основа: устойчивые I, IV и V ступени.", 0,2,4,5,7,9,11),
            s("minor", "Натуральный минор", "Основа многих trap и hip-hop идей; VI и VII дают мягкое движение.", 0,2,3,5,7,8,10),
            s("harmonic_minor", "Гармонический минор", "Повышенная VII тянется к тонике; между VI и VII увеличенная секунда.", 0,2,3,5,7,8,11),
            s("melodic_minor", "Мелодический минор ↑", "Здесь восходящая форма: повышены VI и VII. Классическая нисходящая форма отличается.", 0,2,3,5,7,9,11),
            s("dorian", "Дорийский", "Минор с высокой VI: менее мрачный, удобен для грува.", 0,2,3,5,7,9,10),
            s("phrygian", "Фригийский", "Низкая II рядом с тоникой создаёт напряжение.", 0,1,3,5,7,8,10),
            s("lydian", "Лидийский", "Мажор с высокой IV: открытый, парящий характер.", 0,2,4,6,7,9,11),
            s("mixolydian", "Миксолидийский", "Мажор с низкой VII, подходит для фанкового движения.", 0,2,4,5,7,9,10),
            s("locrian", "Локрийский", "Низкие II и V: тоническое трезвучие уменьшённое, устойчивость слабее.", 0,1,3,5,6,8,10),
            s("minor_pentatonic", "Минорная пентатоника", "Пять нот без II и VI: компактная основа для мелодии.", 0,3,5,7,10),
            s("major_pentatonic", "Мажорная пентатоника", "Пять нот без IV и VII: простой светлый набор.", 0,2,4,7,9),
            s("blues", "Минорный блюзовый лад", "Минорная пентатоника плюс низкая V — блюзовая проходящая нота.", 0,3,5,6,7,10)
    };
    private static final Quality[] QUALITIES = {
            q("major","Мажорное трезвучие","",0,4,7), q("minor","Минорное трезвучие","m",0,3,7),
            q("dim","Уменьшённое","dim",0,3,6), q("aug","Увеличенное","aug",0,4,8),
            q("sus2","С задержанием II","sus2",0,2,7), q("sus4","С задержанием IV","sus4",0,5,7),
            q("maj7","Большой мажорный септаккорд","maj7",0,4,7,11),
            q("min7","Малый минорный септаккорд","m7",0,3,7,10),
            q("dom7","Доминантсептаккорд","7",0,4,7,10),
            q("m7b5","Полууменьшённый","m7♭5",0,3,6,10), q("dim7","Уменьшённый септаккорд","dim7",0,3,6,9),
            q("add9","Мажор с добавленной IX","add9",0,4,7,14),
            q("min_add9","Минор с добавленной IX","m(add9)",0,3,7,14),
            q("major6","Мажор с VI","6",0,4,7,9), q("minor6","Минор с VI","m6",0,3,7,9),
            q("min_maj7","Минор с большой VII","m(maj7)",0,3,7,11),
            q("aug_maj7","Увеличенный с большой VII","aug(maj7)",0,4,8,11)
    };
    private static Scale s(String id,String name,String description,int... intervals) { return new Scale(id,name,description,intervals); }
    private static Quality q(String id,String name,String symbol,int... intervals) { return new Quality(id,name,symbol,intervals); }

    public static List<Scale> scales() {
        List<Scale> result = new ArrayList<>(); for (Scale s : SCALES) result.add(new Scale(s.id,s.name,s.description,s.intervals));
        return Collections.unmodifiableList(result);
    }
    public static Scale scale(String id) {
        for (Scale s : SCALES) if (s.id.equals(id)) return new Scale(s.id,s.name,s.description,s.intervals);
        throw new IllegalArgumentException("Неизвестный лад: " + id);
    }
    public static List<Quality> qualities() {
        List<Quality> result = new ArrayList<>(); for (Quality q : QUALITIES) result.add(new Quality(q.id,q.name,q.symbol,q.intervals));
        return Collections.unmodifiableList(result);
    }
    public static Quality quality(String id) {
        for (Quality q : QUALITIES) if (q.id.equals(id)) return new Quality(q.id,q.name,q.symbol,q.intervals);
        throw new IllegalArgumentException("Неизвестное качество аккорда: " + id);
    }
    private static int pc(int value) { return Math.floorMod(value, 12); }
    public static String pitchName(int pitch, boolean flats) {
        String[] names = flats ? new String[]{"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"}
                : new String[]{"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
        return names[pc(pitch)];
    }
    public static String noteName(int midi, boolean flats) {
        if (midi < 0 || midi > 127) throw new IllegalArgumentException("Нота MIDI вне диапазона.");
        return pitchName(midi, flats) + (midi / 12);
    }
    public static String aliases(int pitch) {
        String sharp = pitchName(pitch,false), flat = pitchName(pitch,true);
        return sharp.equals(flat) ? sharp : sharp + " / " + flat;
    }
    public static int[] scalePitches(int root, String scaleId) {
        int[] pitches = scale(scaleId).intervals;
        for (int i = 0; i < pitches.length; i++) pitches[i] = pc(root + pitches[i]); return pitches;
    }
    public static boolean inScale(int midi, int root, String scaleId) {
        for (int pitch : scalePitches(root,scaleId)) if (pitch == pc(midi)) return true; return false;
    }
    public static String scaleNotes(int root,String id,boolean flats) {
        StringBuilder text = new StringBuilder();
        for (int pitch : scalePitches(root,id)) { if (text.length()>0) text.append(" / "); text.append(pitchName(pitch,flats)); }
        return text.toString();
    }
    public static int[] chordNotes(MusicData.Chord chord) {
        if (chord == null) throw new IllegalArgumentException("Нет аккорда.");
        int[] notes = quality(chord.quality).intervals;
        if (chord.root < 0 || chord.root > 11 || chord.octave < 0 || chord.octave > 8
                || chord.inversion < 0 || chord.inversion >= notes.length) throw new IllegalArgumentException("Некорректное обращение или регистр.");
        for (int i=0;i<notes.length;i++) notes[i] += chord.octave*12+chord.root;
        Arrays.sort(notes);
        for (int turn=0;turn<chord.inversion;turn++) { notes[0]+=12; Arrays.sort(notes); }
        return notes;
    }
    public static String chordName(MusicData.Chord chord,boolean flats) {
        String name = pitchName(chord.root,flats)+quality(chord.quality).symbol;
        int bass = pc(chordNotes(chord)[0]);
        return bass == chord.root ? name : name + "/" + pitchName(bass,flats);
    }
    public static List<MusicData.Chord> diatonicChords(int root,String scaleId,boolean sevenths) {
        int[] intervals=scale(scaleId).intervals; List<MusicData.Chord> result=new ArrayList<>();
        if (intervals.length != 7) return result;
        for(int degree=0;degree<7;degree++) {
            int[] offsets=new int[sevenths?4:3];
            for(int tone=0;tone<offsets.length;tone++) {
                int index=degree+tone*2; offsets[tone]=intervals[index%7]+12*(index/7)-intervals[degree];
            }
            Quality found=matchOffsets(offsets);
            if(found==null) throw new IllegalArgumentException("Это диатоническое качество не представлено в библиотеке.");
            MusicData.Chord chord=new MusicData.Chord(); chord.root=pc(root+intervals[degree]); chord.quality=found.id; result.add(chord);
        }
        return result;
    }
    private static Quality matchOffsets(int[] offsets) {
        int[] expected=offsets.clone(); Arrays.sort(expected);
        for(Quality quality:QUALITIES) { int[] actual=quality.intervals.clone(); Arrays.sort(actual); if(Arrays.equals(expected,actual))return quality; }
        return null;
    }
    public static List<String> identifyChord(int[] midiNotes,boolean flats) {
        Set<String> result=new LinkedHashSet<>();
        if(midiNotes==null||midiNotes.length==0)return new ArrayList<>();
        boolean[] pitches=new boolean[12]; int lowest=128;
        for(int note:midiNotes) { if(note<0||note>127)throw new IllegalArgumentException("Нота MIDI вне диапазона."); pitches[pc(note)]=true;lowest=Math.min(lowest,note); }
        for(int root=0;root<12;root++)for(Quality quality:QUALITIES) {
            boolean[] candidate=new boolean[12]; for(int interval:quality.intervals)candidate[pc(root+interval)]=true;
            if(Arrays.equals(pitches,candidate)) {
                String name=pitchName(root,flats)+quality.symbol;
                if(pc(lowest)!=root)name+="/"+pitchName(lowest,flats); result.add(name);
            }
        }
        return new ArrayList<>(result);
    }

    /** Presets describe scale degrees; quality is derived from the selected mode. */
    public static void applyProgression(MusicData.Project project,String presetId) {
        if(project==null)throw new IllegalArgumentException("Нет проекта.");
        String id="genre".equals(presetId)?project.genre:presetId; int[] degrees; boolean sevenths=false;
        switch(id) {
            case "trap": case "i_VI_iv_VII": degrees=new int[]{0,5,3,6};break;
            case "drill": degrees=new int[]{0,5,6,0};break;
            case "boom_bap": case "west_coast": degrees=new int[]{0,3,4,0};sevenths=true;break;
            case "lofi": degrees=new int[]{1,4,0,5};sevenths=true;break;
            case "phonk": degrees=new int[]{0,6,0,6};break;
            case "plugg": degrees=new int[]{0,5,2,6};sevenths=true;break;
            case "i_iv_v": degrees=new int[]{0,3,4};break;
            case "i_VII_VI_VII": degrees=new int[]{0,6,5,6};break;
            case "i_v": degrees=new int[]{0,4};break;
            case "i_vi_i_vii": degrees=new int[]{0,5,0,6};break;
            case "ii_V_I": degrees=new int[]{1,4,0};sevenths=true;break;
            default: throw new IllegalArgumentException("Неизвестная последовательность: "+presetId);
        }
        String harmonicScale=scale(project.scaleId).intervals.length==7?project.scaleId
                :"major_pentatonic".equals(project.scaleId)?"major":"minor";
        List<MusicData.Chord> derived=diatonicChords(project.root,harmonicScale,sevenths);
        List<MusicData.Chord> chords=new ArrayList<>();
        for(int degree:degrees) { MusicData.Chord source=derived.get(degree), chord=new MusicData.Chord();
            chord.root=source.root;chord.quality=source.quality;chords.add(chord); }
        project.chords=chords;
    }

    public static void generateMelody(MusicData.Project project,int seed,boolean sparse) {
        if(project==null||project.chords==null||project.chords.isEmpty())throw new IllegalArgumentException("Сначала добавь аккорды.");
        int total=MusicData.totalSteps(project); if(total<1||total>2048)throw new IllegalArgumentException("Некорректная длительность.");
        Random random=new Random(seed); List<MusicData.Note> melody=new ArrayList<>();
        int previous=60+pc(project.root), chordStart=0;
        List<Integer> scalar=new ArrayList<>();
        for(int midi=55;midi<=84;midi++)if(inScale(midi,project.root,project.scaleId))scalar.add(midi);
        for(MusicData.Chord chord:project.chords) {
            int chordEnd=chordStart+chord.beats*4, cursor=chordStart; int[] chordTones=chordNotes(chord);
            while(cursor<chordEnd) {
                boolean strong=(cursor-chordStart)%4==0;
                int length=sparse?(random.nextBoolean()?4:2):(random.nextInt(4)==0?4:random.nextBoolean()?2:1);
                length=Math.min(length,chordEnd-cursor);
                if(strong||random.nextDouble()>(sparse?0.42:0.16)) {
                    int midi;
                    if(strong) { int pitch=pc(chordTones[random.nextInt(chordTones.length)]);midi=nearestPitch(pitch,previous,55,84); }
                    else { int index=nearestIndex(scalar,previous); index=Math.max(0,Math.min(scalar.size()-1,index+random.nextInt(3)-1));midi=scalar.get(index); }
                    MusicData.Note note=new MusicData.Note();note.midi=midi;note.start=cursor;note.length=length;
                    note.velocity=strong?88+random.nextInt(15):72+random.nextInt(20);melody.add(note);previous=midi;
                }
                cursor+=length;
            }
            chordStart=chordEnd;
        }
        project.seed=seed;project.melody=melody;
    }
    private static int nearestIndex(List<Integer> notes,int previous) {
        int best=0;for(int i=1;i<notes.size();i++)if(Math.abs(notes.get(i)-previous)<Math.abs(notes.get(best)-previous))best=i;return best;
    }
    private static int nearestPitch(int pitch,int previous,int low,int high) {
        int best=low;int distance=Integer.MAX_VALUE;
        for(int midi=low;midi<=high;midi++)if(pc(midi)==pitch&&Math.abs(midi-previous)<distance){best=midi;distance=Math.abs(midi-previous);}return best;
    }

    public static void transpose(MusicData.Project project,int newRoot) {
        if(project==null||newRoot<0||newRoot>11)throw new IllegalArgumentException("Тоника вне диапазона.");
        int delta=newRoot-project.root;if(delta>6)delta-=12;if(delta<-6)delta+=12;
        for(MusicData.Chord chord:project.chords) {
            int absolute=chord.octave*12+chord.root+delta;
            while(absolute<0)absolute+=12;while(absolute>107)absolute-=12;
            chord.root=pc(absolute);chord.octave=absolute/12;
        }
        for(MusicData.Note note:project.melody) { int midi=note.midi+delta;while(midi<0)midi+=12;while(midi>127)midi-=12;note.midi=midi; }
        project.root=newRoot;
    }

    public static List<MusicData.Note> playbackNotes(MusicData.Project project) {
        return partNotes(project,project.playChords,project.playMelody,project.playBass);
    }
    static List<MusicData.Note> partNotes(MusicData.Project project,boolean chords,boolean melody,boolean bass) {
        MusicData.validateProject(project);List<MusicData.Note> result=new ArrayList<>();int start=0;
        for(MusicData.Chord chord:project.chords) {
            int length=chord.beats*4;
            if(chords)for(int midi:chordNotes(chord))result.add(event(midi,start,length,66));
            if(bass)result.add(event(24+chord.root,start,length,83));
            start+=length;
        }
        if(melody)for(MusicData.Note note:project.melody)result.add(MusicData.copyNote(note));
        result.sort(Comparator.comparingInt((MusicData.Note note)->note.start).thenComparingInt(note->note.midi));return result;
    }
    private static MusicData.Note event(int midi,int start,int length,int velocity) {
        MusicData.Note note=new MusicData.Note();note.midi=midi;note.start=start;note.length=length;note.velocity=velocity;return note;
    }
}
