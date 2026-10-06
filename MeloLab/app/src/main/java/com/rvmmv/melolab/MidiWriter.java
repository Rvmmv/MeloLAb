package com.rvmmv.melolab;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Standard MIDI File type 1, PPQ480; independent conductor and three instrument tracks. */
public final class MidiWriter {
    private MidiWriter() { }
    private static final int PPQ = 480, STEP_TICKS = 120;
    private static final class Event {
        int tick, midi, velocity;
        boolean on;
        Event(int tick,int midi,int velocity,boolean on) { this.tick=tick;this.midi=midi;this.velocity=velocity;this.on=on; }
    }

    /** Audition mute flags do not remove parts from the exported composition. */
    public static byte[] export(MusicData.Project project) {
        MusicData.validateProject(project);
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        ascii(file,"MThd"); integer(file,6); word(file,1); word(file,4); word(file,PPQ);
        int endTick=MusicData.totalSteps(project)*STEP_TICKS;
        track(file,conductor(project.bpm,endTick));
        track(file,instrument("Chords",0,MusicTheory.partNotes(project,true,false,false),endTick));
        track(file,instrument("Melody",1,MusicTheory.partNotes(project,false,true,false),endTick));
        track(file,instrument("Bass",2,MusicTheory.partNotes(project,false,false,true),endTick));
        return file.toByteArray();
    }

    private static byte[] conductor(int bpm,int endTick) {
        ByteArrayOutputStream track=new ByteArrayOutputStream();name(track,"Tempo");
        int tempo=60_000_000/bpm;
        variable(track,0);track.write(0xFF);track.write(0x51);track.write(3);
        track.write((tempo>>>16)&255);track.write((tempo>>>8)&255);track.write(tempo&255);
        variable(track,0);track.write(0xFF);track.write(0x58);track.write(4);
        track.write(4);track.write(2);track.write(24);track.write(8);
        end(track,endTick);return track.toByteArray();
    }

    private static byte[] instrument(String name,int channel,List<MusicData.Note> notes,int endTick) {
        List<Event> events=new ArrayList<>();
        for(MusicData.Note note:notes) {
            events.add(new Event(note.start*STEP_TICKS,note.midi,note.velocity,true));
            events.add(new Event((note.start+note.length)*STEP_TICKS,note.midi,0,false));
        }
        events.sort(Comparator.comparingInt((Event event)->event.tick)
                .thenComparingInt(event->event.on?1:0).thenComparingInt(event->event.midi));
        ByteArrayOutputStream track=new ByteArrayOutputStream();name(track,name);int previous=0;
        for(Event event:events) {
            variable(track,event.tick-previous);track.write((event.on?0x90:0x80)|channel);
            track.write(event.midi);track.write(event.velocity);previous=event.tick;
        }
        end(track,endTick-previous);return track.toByteArray();
    }

    private static void name(ByteArrayOutputStream track,String name) {
        byte[] bytes=name.getBytes(StandardCharsets.UTF_8);
        variable(track,0);track.write(0xFF);track.write(0x03);variable(track,bytes.length);track.write(bytes,0,bytes.length);
    }
    private static void end(ByteArrayOutputStream track,int delta) {
        variable(track,delta);track.write(0xFF);track.write(0x2F);track.write(0);
    }
    private static void track(ByteArrayOutputStream file,byte[] bytes) {
        ascii(file,"MTrk");integer(file,bytes.length);file.write(bytes,0,bytes.length);
    }
    private static void ascii(ByteArrayOutputStream output,String text) {
        byte[] bytes=text.getBytes(StandardCharsets.US_ASCII);output.write(bytes,0,bytes.length);
    }
    private static void word(ByteArrayOutputStream output,int value) { output.write((value>>>8)&255);output.write(value&255); }
    private static void integer(ByteArrayOutputStream output,int value) {
        output.write((value>>>24)&255);output.write((value>>>16)&255);output.write((value>>>8)&255);output.write(value&255);
    }
    private static void variable(ByteArrayOutputStream output,int value) {
        if(value<0||value>0x0FFFFFFF)throw new IllegalArgumentException("Дельта MIDI вне диапазона.");
        int buffer=value&0x7F;
        while((value>>>=7)>0)buffer=(buffer<<8)|((value&0x7F)|0x80);
        for(;;) { output.write(buffer&0xFF);if((buffer&0x80)==0)break;buffer>>>=8; }
    }
}
