package com.myapps.mydictation;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

final class PcmRecorder {
    static final int RATE=16000;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private AudioRecord recorder;
    private Thread thread;
    private volatile Exception error;
    private File output;
    synchronized void start(File file) throws Exception {
        if(running.get())throw new IllegalStateException("Already recording");
        int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        if(min<=0)throw new IOException("Microphone not supported");
        recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,3200)*2);
        if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();recorder=null;throw new IOException("Could not open microphone");}
        output=file;error=null;running.set(true);recorder.startRecording();
        thread=new Thread(()->{
            byte[] bytes=new byte[3200];
            try(FileOutputStream out=new FileOutputStream(file)){
                while(running.get()){
                    int n=recorder.read(bytes,0,bytes.length);
                    if(n>0)out.write(bytes,0,n);else if(n<0)throw new IOException("Microphone read error: "+n);
                }
                out.getFD().sync();
            }catch(Exception e){error=e;}finally{running.set(false);}
        },"my-dictation-recorder");thread.start();
    }
    boolean isRecording(){return running.get();}
    File stop() throws Exception {
        running.set(false);
        if(thread!=null){thread.join(4000);if(thread.isAlive()){try{recorder.stop();}catch(Exception ignored){}thread.join(1000);if(thread.isAlive())throw new IOException("Recorder did not finish");}}
        if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}
        if(recorder!=null){recorder.release();recorder=null;}
        if(error!=null)throw error;
        if(output==null||!output.isFile()||output.length()<3200)throw new IOException("Recording is empty or too short");
        return output;
    }
    void release(){running.set(false);try{if(recorder!=null)recorder.stop();}catch(Exception ignored){}try{if(thread!=null)thread.join(1000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}try{if(recorder!=null)recorder.release();}catch(Exception ignored){}recorder=null;}
}
