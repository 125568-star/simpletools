package com.myapps.mydictation;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Android 13+ prerecorded PCM streaming into the platform recognition service.
 *  Recognition service support depends on the installed speech provider. */
final class PcmTranscriber {
    interface Callback {void success(String text);void failure(String reason);}
    private SpeechRecognizer recognizer;
    private ParcelFileDescriptor readFd,writeFd;
    private final AtomicBoolean completed=new AtomicBoolean(false);
    void start(Context context,File pcm,Callback callback){
        completed.set(false);
        if(!SpeechRecognizer.isRecognitionAvailable(context)){callback.failure("No speech recognition service is installed");return;}
        try {
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();readFd=pipe[0];writeFd=pipe[1];
            recognizer=SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(new RecognitionListener(){
                @Override public void onReadyForSpeech(Bundle params){}
                @Override public void onBeginningOfSpeech(){}
                @Override public void onRmsChanged(float rmsdB){}
                @Override public void onBufferReceived(byte[] buffer){}
                @Override public void onEndOfSpeech(){}
                @Override public void onPartialResults(Bundle partialResults){}
                @Override public void onEvent(int eventType,Bundle params){}
                @Override public void onError(int error){fail("Speech recognition error "+error);}
                @Override public void onResults(Bundle results){ArrayList<String> texts=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(texts==null||texts.isEmpty()||texts.get(0).trim().isEmpty())fail("No speech recognised; audio kept for retry");else ok(texts.get(0).trim());}
                void ok(String text){if(completed.compareAndSet(false,true)){callback.success(text);close();}}
                void fail(String reason){if(completed.compareAndSet(false,true)){callback.failure(reason);close();}}
            });
            Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE,readFd);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT,1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE,PcmRecorder.RATE);
            recognizer.startListening(intent);
            new Thread(()->feedAudio(pcm),"my-dictation-playback").start();
        }catch(Exception e){if(completed.compareAndSet(false,true))callback.failure("Cannot start transcription: "+e.getMessage());close();}
    }
    private void feedAudio(File pcm){
        try(InputStream in=new FileInputStream(pcm);OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(writeFd)){
            writeFd=null;byte[] buf=new byte[3200];int n;
            while(!completed.get()&&(n=in.read(buf))!=-1){out.write(buf,0,n);out.flush();Thread.sleep(Math.max(1,n*1000/(PcmRecorder.RATE*2)));}
        }catch(Exception ignored){/* RecognitionListener reports provider failure; source file remains intact. */}
    }
    void close(){try{if(recognizer!=null){recognizer.cancel();recognizer.destroy();}}catch(Exception ignored){}recognizer=null;try{if(readFd!=null)readFd.close();}catch(Exception ignored){}readFd=null;try{if(writeFd!=null)writeFd.close();}catch(Exception ignored){}writeFd=null;}
}
