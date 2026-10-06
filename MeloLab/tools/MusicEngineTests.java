package com.rvmmv.melolab;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Production theory plus an independent byte-level Standard MIDI parser. */
public final class MusicEngineTests {
    private static int assertions;
    public static void main(String[] args) {
        facts(); chordRecognition(); modesAndPresets(); melodyAndTranspose(); playbackAndValidation(); midiRoundTrip();
        System.out.println("Music engine/MIDI tests passed: 6 groups, " + assertions + " assertions");
    }
    private static void facts() {
        eq(12,MusicTheory.scales().size(),"12 scales");eq(17,MusicTheory.qualities().size(),"17 chord qualities");
        eq("C5",MusicTheory.noteName(60,false),"FL convention middle C");eq("C0",MusicTheory.noteName(0,false),"Lowest MIDI label");
        eq("G10",MusicTheory.noteName(127,false),"Highest MIDI label");eq("G# / Ab",MusicTheory.aliases(8),"Enharmonics");
        eq("F / G / G# / A# / C / C# / D#",MusicTheory.scaleNotes(5,"minor",false),"F natural minor");
        array(new int[]{0,2,4,5,7,9,11},MusicTheory.scale("major").intervals,"Major facts");
        array(new int[]{0,2,3,5,7,8,10},MusicTheory.scale("minor").intervals,"Minor facts");
        array(new int[]{0,2,3,5,7,8,11},MusicTheory.scale("harmonic_minor").intervals,"Harmonic minor facts");
        array(new int[]{0,2,3,5,7,9,11},MusicTheory.scale("melodic_minor").intervals,"Ascending melodic minor facts");
        array(new int[]{0,2,3,5,7,9,10},MusicTheory.scale("dorian").intervals,"Dorian facts");
        array(new int[]{0,1,3,5,7,8,10},MusicTheory.scale("phrygian").intervals,"Phrygian facts");
        array(new int[]{0,2,4,6,7,9,11},MusicTheory.scale("lydian").intervals,"Lydian facts");
        array(new int[]{0,2,4,5,7,9,10},MusicTheory.scale("mixolydian").intervals,"Mixolydian facts");
        array(new int[]{0,1,3,5,6,8,10},MusicTheory.scale("locrian").intervals,"Locrian facts");
        array(new int[]{0,3,5,7,10},MusicTheory.scale("minor_pentatonic").intervals,"Minor pentatonic facts");
        array(new int[]{0,2,4,7,9},MusicTheory.scale("major_pentatonic").intervals,"Major pentatonic facts");
        array(new int[]{0,3,5,6,7,10},MusicTheory.scale("blues").intervals,"Blues facts");
        MusicTheory.scale("major").intervals[0]=99;eq(0,MusicTheory.scale("major").intervals[0],"Scale catalog isolated from caller");
        MusicTheory.quality("minor").intervals[1]=99;eq(3,MusicTheory.quality("minor").intervals[1],"Quality catalog isolated");
        MusicData.Chord fm=new MusicData.Chord();array(new int[]{53,56,60},MusicTheory.chordNotes(fm),"Fm voicing");
        fm.inversion=1;array(new int[]{56,60,65},MusicTheory.chordNotes(fm),"Fm first inversion");eq("Fm/G#",MusicTheory.chordName(fm,false),"Slash bass");
        fm.inversion=2;array(new int[]{60,65,68},MusicTheory.chordNotes(fm),"Fm second inversion");
        MusicData.Chord add=new MusicData.Chord();add.root=0;add.quality="add9";
        array(new int[]{48,52,55,62},MusicTheory.chordNotes(add),"Added9 retains compound interval");
        add.inversion=1;array(new int[]{52,55,60,62},MusicTheory.chordNotes(add),"Compound chord inversion sorted");
        for(MusicTheory.Quality quality:MusicTheory.qualities())for(int root=0;root<12;root++)for(int inversion=0;inversion<quality.intervals.length;inversion++) {
            MusicData.Chord chord=new MusicData.Chord();chord.root=root;chord.octave=8;chord.quality=quality.id;chord.inversion=inversion;
            for(int midi:MusicTheory.chordNotes(chord))check(midi>=0&&midi<=127,"All allowed voicings stay in MIDI range");
        }
    }
    private static void chordRecognition() {
        check(MusicTheory.identifyChord(new int[]{60,64,67},false).contains("C"),"Recognize C major");
        check(MusicTheory.identifyChord(new int[]{56,60,65},true).contains("Fm/Ab"),"Recognize inversion and flat spelling");
        List<String> ambiguous=MusicTheory.identifyChord(new int[]{60,64,67,69},false);
        check(ambiguous.contains("C6")&&ambiguous.contains("Am7/C"),"Preserve valid ambiguous chord names");
        check(MusicTheory.identifyChord(new int[]{60,63,66,69},false).size()>=4,"Diminished seventh ambiguity");
        check(MusicTheory.identifyChord(new int[]{60,60,64,67,72},false).contains("C"),"Ignore octave duplicates for recognition");
        check(MusicTheory.identifyChord(new int[]{60,61},false).isEmpty(),"Unknown cluster does not get fake match");
        check(MusicTheory.identifyChord(null,false).isEmpty(),"Empty recognition");
    }
    private static void modesAndPresets() {
        for(MusicTheory.Scale scale:MusicTheory.scales())for(int root=0;root<12;root++)for(boolean seventh:new boolean[]{false,true}) {
            List<MusicData.Chord> chords=MusicTheory.diatonicChords(root,scale.id,seventh);
            eq(scale.intervals.length==7?7:0,chords.size(),"Diatonic chord count");
            for(MusicData.Chord chord:chords)for(int midi:MusicTheory.chordNotes(chord))
                check(MusicTheory.inScale(midi,root,scale.id),"Derived chord tones belong to selected scale");
        }
        List<MusicData.Chord> minor=MusicTheory.diatonicChords(5,"minor",false);
        eq("minor",minor.get(0).quality,"Minor tonic");eq("dim",minor.get(1).quality,"Minor second diminished");
        eq("min_maj7",MusicTheory.diatonicChords(5,"harmonic_minor",true).get(0).quality,"Harmonic-minor tonic seventh");
        eq("aug_maj7",MusicTheory.diatonicChords(5,"melodic_minor",true).get(2).quality,"Melodic-minor third seventh");
        String[] ids={"genre","i_VI_iv_VII","i_iv_v","i_VII_VI_VII","i_v","i_vi_i_vii","ii_V_I",
                "trap","drill","boom_bap","lofi","phonk","plugg","west_coast"};
        for(String id:ids)for(MusicTheory.Scale scale:MusicTheory.scales()) {
            MusicData.Project project=new MusicData.Project();project.scaleId=scale.id;MusicTheory.applyProgression(project,id);
            MusicData.validateProject(project);check(!project.chords.isEmpty(),"Every documented preset produces real chords");
        }
        MusicData.Project major=new MusicData.Project();major.root=0;major.scaleId="major";MusicTheory.applyProgression(major,"ii_V_I");
        eq(2,major.chords.get(0).root,"Major ii root");eq("min7",major.chords.get(0).quality,"Major ii quality");
        eq("dom7",major.chords.get(1).quality,"Major V7");eq("maj7",major.chords.get(2).quality,"Major Imaj7");
        expectBad(()->MusicTheory.applyProgression(major,"unknown"),"Reject unknown preset");
    }
    private static void melodyAndTranspose() {
        for(MusicTheory.Scale scale:MusicTheory.scales())for(int root=0;root<12;root++)for(boolean sparse:new boolean[]{false,true}) {
            MusicData.Project project=new MusicData.Project();project.root=root;project.scaleId=scale.id;
            MusicTheory.applyProgression(project,"genre");MusicTheory.generateMelody(project,73,sparse);MusicData.validateProject(project);
            MusicData.Project repeat=MusicData.copy(project);MusicTheory.generateMelody(repeat,73,sparse);
            eq(signature(project.melody),signature(repeat.melody),"Generation deterministic across modes/roots");
            int previousEnd=0;
            for(MusicData.Note note:project.melody) {check(note.start>=previousEnd,"Generated phrase monophonic");previousEnd=note.start+note.length;}
            check(!project.melody.isEmpty(),"No empty generated phrase");
        }
        MusicData.Project project=MusicData.defaults().projects.get(0);String before=signature(project.melody);
        MusicTheory.generateMelody(project,909,true);check(!before.equals(signature(project.melody)),"Different seed changes idea");
        project.melody.clear();project.melody.add(note(0,0,4,99));project.melody.add(note(127,8,4,80));
        List<String> qualities=new ArrayList<>();for(MusicData.Chord chord:project.chords)qualities.add(chord.quality);
        MusicTheory.transpose(project,11);eq(11,project.root,"New tonic");eq(6,project.melody.get(0).midi,"Transpose low note");
        eq(121,project.melody.get(1).midi,"Boundary wraps octave to preserve pitch class");MusicData.validateProject(project);
        for(int i=0;i<qualities.size();i++)eq(qualities.get(i),project.chords.get(i).quality,"Transpose preserves chord quality");
        eq(8,project.melody.get(1).start,"Transpose preserves timing");
    }
    private static void playbackAndValidation() {
        MusicData.State state=MusicData.defaults();MusicData.validate(state);eq(1,state.projects.size(),"One explicit starter idea");
        MusicData.Project project=MusicData.current(state);MusicData.Project copy=MusicData.copy(project);
        eq(project.id,copy.id,"Copy preserves ID for caller-controlled duplication");copy.melody.get(0).midi=0;
        check(project.melody.get(0).midi!=0,"Copy deeply detaches notes");
        project.playChords=false;project.playMelody=false;project.playBass=false;
        check(MusicTheory.playbackNotes(project).isEmpty(),"Audition mute flags apply");
        project.playBass=true;List<MusicData.Note> bass=MusicTheory.playbackNotes(project);eq(project.chords.size(),bass.size(),"One bass root per chord");
        for(int i=0;i<bass.size();i++)eq(24+project.chords.get(i).root,bass.get(i).midi,"Bass FL octave2");
        project.playBass=false;project.playMelody=true;
        List<MusicData.Note> melody=MusicTheory.playbackNotes(project);eq(project.melody.size(),melody.size(),"Melody-only audition");
        melody.get(0).midi=0;check(project.melody.get(0).midi!=0,"Playback notes do not mutate project");
        project.melody.add(note(66,1,3,100));MusicData.validateProject(project);check(!MusicTheory.inScale(66,5,"minor"),"Non-diatonic fact remains allowed");
        MusicData.Project invalid=MusicData.copy(project);invalid.melody.get(0).length=MusicData.totalSteps(project)+1;
        expectBad(()->MusicData.validateProject(invalid),"Reject notes outside duration");
        invalid.melody.get(0).length=1;invalid.bpm=0;expectBad(()->MusicData.validateProject(invalid),"Reject invalid BPM");
        state.projects.add(MusicData.copy(project));expectBad(()->MusicData.validate(state),"Reject duplicate project IDs");
        MusicData.State missing=MusicData.defaults();missing.currentId="missing";expectBad(()->MusicData.validate(missing),"Reject unresolved current project");
    }
    private static void midiRoundTrip() {
        MusicData.Project project=MusicData.defaults().projects.get(0);project.melody.clear();
        project.melody.add(note(60,0,8,95));project.melody.add(note(64,0,4,88));
        project.melody.add(note(60,2,8,77));project.melody.add(note(64,4,2,91));project.melody.add(note(127,12,4,110));
        byte[] full=MidiWriter.export(project);project.playChords=false;project.playMelody=false;project.playBass=false;
        check(Arrays.equals(full,MidiWriter.export(project)),"MIDI export includes all parts regardless mutes");
        ParsedMidi parsed=parse(full);eq(1,parsed.format,"SMFtype1");eq(480,parsed.ppq,"PPQ480");eq(4,parsed.tracks.size(),"Four tracks");
        eq(60_000_000/project.bpm,parsed.tracks.get(0).tempo,"Correct tempo meta");
        array(new int[]{4,2,24,8},parsed.tracks.get(0).meter,"4/4 time signature");
        String[] names={"Tempo","Chords","Melody","Bass"};
        for(int i=0;i<4;i++) {ParsedTrack track=parsed.tracks.get(i);eq(names[i],track.name,"Namedtrack");
            eq(MusicData.totalSteps(project)*120,track.endTick,"EOT at full duration");check(track.activeAtEnd==0,"No stuck MIDI notes");
            check(track.noteOns==track.noteOffs,"Every MIDI note has an off event");}
        ParsedTrack melody=parsed.tracks.get(2);eq(project.melody.size(),melody.notes.size(),"Polyphonic melody preserved");
        for(MusicData.Note source:project.melody) {
            boolean found=false;for(ParsedNote note:melody.notes)if(note.midi==source.midi&&note.start==source.start*120
                    &&note.end==(source.start+source.length)*120&&note.velocity==source.velocity)found=true;
            check(found,"Independent parser roundtrips pitch/start/end/velocity");
        }
        for(int index=1;index<4;index++)for(ParsedNote note:parsed.tracks.get(index).notes) {
            eq(index-1,note.channel,"Separate MIDI channels");check(note.start%120==0&&note.end%120==0,"Sixteenth timing120ticks");
            check(note.midi>=0&&note.midi<=127&&note.end>note.start,"Legal MIDI notes");
        }
        MusicData.Project longest=new MusicData.Project();for(int i=0;i<32;i++){MusicData.Chord c=new MusicData.Chord();c.beats=16;longest.chords.add(c);}
        longest.bpm=40;ParsedMidi max=parse(MidiWriter.export(longest));eq(245760,max.tracks.get(3).endTick,"Multi-byte VLQ long timeline");
        eq(1_500_000,max.tracks.get(0).tempo,"Slowtempo");
    }

    private static final class ParsedMidi { int format,ppq;List<ParsedTrack> tracks=new ArrayList<>(); }
    private static final class ParsedTrack { String name="";int tempo,endTick,noteOns,noteOffs,activeAtEnd;int[] meter;List<ParsedNote> notes=new ArrayList<>(); }
    private static final class ParsedNote { int midi,start,end,velocity,channel;ParsedNote(int midi,int start,int velocity,int channel){this.midi=midi;this.start=start;this.velocity=velocity;this.channel=channel;} }
    /** Intentionally independent: reads chunks, VLQs, meta and channel events from exported bytes. */
    private static ParsedMidi parse(byte[] bytes) {
        ByteBuffer input=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);ParsedMidi result=new ParsedMidi();
        eq("MThd",ascii(input,4),"MIDI header signature");eq(6,input.getInt(),"Headerlength");
        result.format=input.getShort()&65535;int count=input.getShort()&65535;result.ppq=input.getShort()&65535;
        for(int t=0;t<count;t++) {
            eq("MTrk",ascii(input,4),"Track chunk signature");int length=input.getInt();
            check(length>=0&&length<=input.remaining(),"Tracklength bounded");int end=input.position()+length,tick=0,lastTick=-1;
            boolean sawNoteOnAtTick=false,sawEnd=false;ParsedTrack track=new ParsedTrack();
            Map<Integer,ArrayDeque<ParsedNote>> active=new HashMap<>();
            while(input.position()<end) {
                tick+=vlq(input);int status=input.get()&255;
                if(tick!=lastTick){sawNoteOnAtTick=false;lastTick=tick;}
                if(status==255) {int type=input.get()&255,n=vlq(input);check(n<=end-input.position(),"Meta length withintrack");byte[] data=new byte[n];input.get(data);
                    if(type==3)track.name=new String(data,StandardCharsets.UTF_8);
                    if(type==81){eq(3,n,"Tempo length");track.tempo=((data[0]&255)<<16)|((data[1]&255)<<8)|(data[2]&255);}
                    if(type==88){track.meter=new int[n];for(int i=0;i<n;i++)track.meter[i]=data[i]&255;}
                    if(type==47){eq(0,n,"Endoftracklength");track.endTick=tick;sawEnd=true;check(input.position()==end,"EOT is final event");}
                } else {
                    check((status&240)==128||(status&240)==144,"Expected notechannel event");int midi=input.get()&255,velocity=input.get()&255;
                    check(midi<128&&velocity<128,"MIDI databytes7bit");int channel=status&15,key=channel*128+midi;
                    boolean on=(status&240)==144&&velocity>0;
                    if(on) {sawNoteOnAtTick=true;track.noteOns++;active.computeIfAbsent(key,k->new ArrayDeque<>()).add(new ParsedNote(midi,tick,velocity,channel));}
                    else {check(!sawNoteOnAtTick,"Noteoff before noteon atsame tick");track.noteOffs++;
                        ArrayDeque<ParsedNote> stack=active.get(key);check(stack!=null&&!stack.isEmpty(),"Noteoff has matching noteon");
                        ParsedNote note=stack.remove();note.end=tick;track.notes.add(note);}
                }
            }
            check(sawEnd,"Track has EOT");for(ArrayDeque<ParsedNote> stack:active.values())track.activeAtEnd+=stack.size();result.tracks.add(track);
        }
        check(!input.hasRemaining(),"No unexplained bytes after MIDItracks");return result;
    }
    private static int vlq(ByteBuffer input) {int value=0;for(int i=0;i<4;i++){int b=input.get()&255;value=(value<<7)|(b&127);if((b&128)==0)return value;}throw new AssertionError("Overlong MIDI VLQ");}
    private static String ascii(ByteBuffer input,int length){byte[] bytes=new byte[length];input.get(bytes);return new String(bytes,StandardCharsets.US_ASCII);}
    private static MusicData.Note note(int midi,int start,int length,int velocity){MusicData.Note note=new MusicData.Note();note.midi=midi;note.start=start;note.length=length;note.velocity=velocity;return note;}
    private static String signature(List<MusicData.Note> notes){StringBuilder out=new StringBuilder();for(MusicData.Note n:notes)out.append(n.midi).append(':').append(n.start).append(':').append(n.length).append(':').append(n.velocity).append(';');return out.toString();}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void eq(Object expected,Object actual,String message){check(expected.equals(actual),message+": expected "+expected+", got "+actual);}
    private static void array(int[] expected,int[] actual,String message){check(Arrays.equals(expected,actual),message);}
    private static void expectBad(Runnable action,String message){boolean threw=false;try{action.run();}catch(IllegalArgumentException error){threw=true;}check(threw,message);}
}
