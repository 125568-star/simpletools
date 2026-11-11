package com.myapps.mydictation;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.text.TextWatcher;
import android.text.Editable;
import android.graphics.drawable.GradientDrawable;
import java.io.File;
import java.util.*;

public final class NoteActivity extends Activity {
    private static final int MIC_PERMISSION=71;
    private Database db;
    private long noteId;
    private Note note;
    private EditText titleEdit,bodyEdit;
    private LinearLayout base,itemsBox;
    private TextView status,folderNameView;
    private LinearLayout recIndicator;
    private boolean autoSaving=false;
    private final Runnable autosave=()->saveEdits();
    private Button recordBtn,stopBtn;
    // Preserve the note's text insertion point even after tapping the bottom controls.
    private int dictatedSelectionStart=-1,dictatedSelectionEnd=-1;
    private PcmRecorder recorder;
    private PcmTranscriber transcriber;
    private LiveTranscriber liveTranscriber;
    private boolean liveRecording=false;
    private boolean transcriptionRunning=false,exitAfterTranscript=false,starting=false;
    private File recordingFile,recordingDir;
    private long recordStart;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable timer=new Runnable(){@Override public void run(){if((liveRecording||(recorder!=null&&recorder.isRecording()))&&status!=null){long s=(System.currentTimeMillis()-recordStart)/1000;status.setText("● Recording  "+(s/60)+":"+String.format(Locale.ROOT,"%02d",s%60));handler.postDelayed(this,500);}}};

    @Override public void onCreate(Bundle b){super.onCreate(b);db=new Database(this);noteId=getIntent().getLongExtra("id",-1);note=db.read(noteId);if(note==null){finish();return;}recordingDir=new File(getFilesDir(),"recordings");recordingDir.mkdirs();render();}
    private void render(){
        note=db.read(noteId);if(note==null)return;
        base=Ui.vertical(this);base.setBackgroundColor(Ui.BG);
        // The REC lamp is centred independently of the Back button.
        FrameLayout header=new FrameLayout(this);
        Button back=Ui.secondary(this,"‹ Folders");
        FrameLayout.LayoutParams backLp=new FrameLayout.LayoutParams(-2,-2,Gravity.START|Gravity.CENTER_VERTICAL);
        header.addView(back,backLp);
        back.setOnClickListener(v->leave());
        recIndicator=Ui.horizontal(this);
        View dot=new View(this);
        GradientDrawable dotBg=Ui.shape(Color.rgb(241,54,54),this);
        dotBg.setCornerRadius(Ui.dp(this,50));
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp=new LinearLayout.LayoutParams(Ui.dp(this,9),Ui.dp(this,9));
        dotLp.rightMargin=Ui.dp(this,6);
        recIndicator.addView(dot,dotLp);
        TextView recLabel=Ui.text(this,"REC",14);
        recLabel.setTypeface(null,android.graphics.Typeface.BOLD);
        recLabel.setTextColor(Color.rgb(255,98,98));
        recIndicator.addView(recLabel);
        FrameLayout.LayoutParams recLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        recLp.topMargin=Ui.dp(this,16);
        header.addView(recIndicator,recLp);
        Ui.add(base,header,0);
        TextView heading=Ui.heading(this,note.kind.equals("note")?"Dictation":"Checklist");
        Ui.add(base,heading,8);
        ScrollView scroll=new ScrollView(this);LinearLayout content=Ui.vertical(this);Ui.pad(content,this,4);scroll.addView(content);
        titleEdit=Ui.edit(this,"Title");titleEdit.setText(note.title);titleEdit.setTextSize(22);Ui.add(content,titleEdit,8);
        LinearLayout tools=Ui.horizontal(this);
        Button folder=Ui.secondary(this,"Put into Folder");tools.addView(folder,new LinearLayout.LayoutParams(0,-2,1));folder.setOnClickListener(v->chooseFolder());
        Button color=Ui.secondary(this,"Colour");tools.addView(color,new LinearLayout.LayoutParams(0,-2,1));color.setOnClickListener(v->chooseColour());
        Ui.add(content,tools,8);
        folderNameView=Ui.text(this,"Folder: "+note.folder,13);
        folderNameView.setTextColor(Ui.MUTED);
        Ui.add(content,folderNameView,6);
        bodyEdit=Ui.edit(this,"Write or dictate your note...");
        bodyEdit.setText(note.body);
        bodyEdit.setMinLines(note.kind.equals("note")?7:2);
        bodyEdit.setGravity(Gravity.TOP|Gravity.START);
        // An actual outlined, black writing area, with readable white text.
        GradientDrawable editor=Ui.shape(Color.BLACK,this);
        editor.setStroke(Ui.dp(this,1.5f),Color.rgb(104,127,180));
        editor.setCornerRadius(Ui.dp(this,12));
        bodyEdit.setBackground(editor);
        bodyEdit.setTextColor(Color.WHITE);
        bodyEdit.setHintTextColor(Color.rgb(160,160,160));
        Ui.pad(bodyEdit,this,12);
        Ui.add(content,bodyEdit,12);
        TextWatcher editWatcher=new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){scheduleAutosave();}
            @Override public void afterTextChanged(Editable e){}
        };
        titleEdit.addTextChangedListener(editWatcher);
        bodyEdit.addTextChangedListener(editWatcher);
        if(!"note".equals(note.kind)){
            itemsBox=Ui.vertical(this);Ui.add(content,itemsBox,12);renderItems();
            LinearLayout addLine=Ui.horizontal(this);EditText itemText=Ui.edit(this,"New checklist item");addLine.addView(itemText,new LinearLayout.LayoutParams(0,-2,1));
            Button add=Ui.button(this,"Add");addLine.addView(add);add.setOnClickListener(v->{db.addItem(noteId,itemText.getText().toString());itemText.setText("");renderItems();});Ui.add(content,addLine,8);
        }
        base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout bottom=Ui.vertical(this);Ui.pad(bottom,this,10);bottom.setBackground(Ui.shape(Ui.CARD,this));
        status=Ui.text(this,"Ready to dictate",14);status.setTextColor(Ui.MUTED);bottom.addView(status);
        LinearLayout actions=Ui.horizontal(this);
        // Separate tape-recorder controls. No recording on opening a note.
        recordBtn=Ui.button(this,"●  Record");
        actions.addView(recordBtn,new LinearLayout.LayoutParams(0,-2,1));
        recordBtn.setOnClickListener(v->beginRecording());
        stopBtn=Ui.secondary(this,"■  Stop");
        LinearLayout.LayoutParams stopLp=new LinearLayout.LayoutParams(0,-2,1);
        stopLp.leftMargin=Ui.dp(this,8);
        actions.addView(stopBtn,stopLp);
        stopBtn.setOnClickListener(v->endAndTranscribe());
        Ui.add(bottom,actions,8);
        Ui.add(base,bottom,0);Ui.pad(base,this,14);setContentView(base);updateRecordingButtons();
        if(note.audioState.equals("PENDING")&&!transcriptionRunning){status.setText("Untranscribed recording saved — retry available");Button retry=Ui.secondary(this,"Retry transcription");Ui.add(bottom,retry,5);retry.setOnClickListener(v->retry());}
        if(note.audioState.equals("VERIFIED")){status.setText("Transcript saved — audio deletion available");Button deletion=Ui.secondary(this,"Delete transcribed audio");Ui.add(bottom,deletion,5);deletion.setOnClickListener(v->askDeleteAudio(false));}
    }
    private void renderItems(){if(itemsBox==null)return;itemsBox.removeAllViews();for(Database.ChecklistItem item:db.items(noteId)){
        LinearLayout row=Ui.horizontal(this);row.setBackground(Ui.shape(Ui.CARD,this));Ui.pad(row,this,4);
        CheckBox box=new CheckBox(this);box.setButtonTintList(android.content.res.ColorStateList.valueOf(Ui.PRIMARY));box.setText(item.text);box.setTextColor(Ui.TEXT);box.setChecked(item.checked);row.addView(box,new LinearLayout.LayoutParams(0,-2,1));box.setOnCheckedChangeListener((b,checked)->db.checked(item.id,checked));
        Button options=Ui.secondary(this,"⋮");row.addView(options);options.setOnClickListener(v->itemActions(item));Ui.add(itemsBox,row,6);
    }}
    private void itemActions(Database.ChecklistItem item){new AlertDialog.Builder(this).setTitle(item.text).setItems(new String[]{"Edit","Move up","Move down","Delete"},(d,which)->{
        if(which==0){EditText e=Ui.edit(this,"Item");e.setText(item.text);new AlertDialog.Builder(this).setTitle("Edit item").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,y)->{db.renameItem(item.id,e.getText().toString());renderItems();}).show();}
        else if(which==1||which==2){db.moveItem(noteId,item.id,which==1?-1:1);renderItems();}
        else{db.deleteItem(item.id);renderItems();}
    }).show();}
    private void chooseFolder(){String[] names=db.folders().toArray(new String[0]);String[] options=Arrays.copyOf(names,names.length+1);options[names.length]="+ New Folder";
        new AlertDialog.Builder(this).setTitle("Put into Folder").setItems(options,(d,which)->{
            if(which==names.length){EditText e=Ui.edit(this,"Folder name");new AlertDialog.Builder(this).setTitle("New Folder").setView(e).setNegativeButton("Cancel",null).setPositiveButton("Save",(x,y)->{String name=e.getText().toString().trim();if(!name.isEmpty()){saveEdits();db.setFolder(noteId,name);render();}}).show();}
            else{saveEdits();db.setFolder(noteId,options[which]);render();}
        }).show();}
    private void chooseColour(){final String[] colours={"Default dark","Yellow","Blue","Green","Orange","Pink","Grey","White"};final int[] values={Ui.CARD,0xFFFFF4AB,0xFFBEDAFF,0xFFC7EFD0,0xFFFFD2A8,0xFFFFC8E8,0xFFD5D9E1,0xFFFFFFFF};new AlertDialog.Builder(this).setTitle("Note background colour").setItems(colours,(d,i)->{saveEdits();db.setColor(noteId,values[i]);render();}).show();}
    private void scheduleAutosave(){
        handler.removeCallbacks(autosave);
        handler.postDelayed(autosave,450);
    }
    private void saveEdits(){
        handler.removeCallbacks(autosave);
        if(autoSaving||db==null||titleEdit==null||bodyEdit==null)return;
        autoSaving=true;
        try{db.update(noteId,titleEdit.getText().toString(),bodyEdit.getText().toString());}
        finally{autoSaving=false;}
    }
    private void updateRecordingButtons(){
        boolean active=liveRecording||(recorder!=null&&recorder.isRecording());
        if(recIndicator!=null)recIndicator.setVisibility(active?View.VISIBLE:View.INVISIBLE);
        if(recordBtn==null||stopBtn==null)return;
        recordBtn.setEnabled(!active&&!transcriptionRunning);
        stopBtn.setEnabled(active);
        recordBtn.setText("●  Record");
        stopBtn.setText("■  Stop");
        if(active)status.setText("● Recording...");
        else if(transcriptionRunning)status.setText("Transcribing...");
    }
    private void beginRecording(){
        if(starting||transcriptionRunning||liveRecording||(recorder!=null&&recorder.isRecording()))return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_PERMISSION);
            return;
        }
        starting=true;
        try{
            saveEdits();
            // Keep the caret/selection where the user last placed it. Clicking
            // Record can remove keyboard focus but must not reset insertion.
            if(bodyEdit!=null){
                dictatedSelectionStart=bodyEdit.getSelectionStart();
                dictatedSelectionEnd=bodyEdit.getSelectionEnd();
            }
            // Use the Android speech provider's live microphone input. The old
            // prerecorded-PCM path was not supported by every recognition service.
            LiveTranscriber next=new LiveTranscriber();
            liveTranscriber=next;
            liveRecording=true;
            recordStart=System.currentTimeMillis();
            handler.post(timer);
            next.start(this,new LiveTranscriber.Callback(){
                @Override public void partial(String words){
                    if(liveRecording && status!=null)
                        status.setText("● Listening: "+words);
                }
                @Override public void success(String text){
                    runOnUiThread(()->{
                        if(liveTranscriber!=next)return;
                        liveTranscriber=null;
                        liveRecording=false;
                        transcriptionRunning=false;
                        handler.removeCallbacks(timer);
                        try{
                            // Insert at the note editor's saved text caret, not at
                            // the end of the note. The note is autosaved afterward.
                            insertDictatedText(text);
                            boolean leaving=exitAfterTranscript;
                            exitAfterTranscript=false;
                            if(leaving)finish();
                            else{
                                updateRecordingButtons();
                                if(status!=null)status.setText("Transcription inserted and saved");
                            }
                        }catch(Exception e){
                            render();
                            showError("Text could not be saved",e.getMessage());
                        }
                    });
                }
                @Override public void failure(String reason){
                    runOnUiThread(()->{
                        if(liveTranscriber!=next)return;
                        liveTranscriber=null;
                        liveRecording=false;
                        transcriptionRunning=false;
                        handler.removeCallbacks(timer);
                        exitAfterTranscript=false;
                        render();
                        showError("Transcription failed",reason+"\nTry recording again. Live dictation does not store an audio backup.");
                    });
                }
            });
            updateRecordingButtons();
        }catch(Exception e){
            liveRecording=false;
            if(liveTranscriber!=null)liveTranscriber.close();
            liveTranscriber=null;
            handler.removeCallbacks(timer);
            showError("Recording could not start",e.getMessage());
        }finally{starting=false;}
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){super.onRequestPermissionsResult(request,perms,grants);if(request==MIC_PERMISSION){if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)beginRecording();else Toast.makeText(this,"Microphone permission is needed for dictation",Toast.LENGTH_LONG).show();}}
    private void insertDictatedText(String spoken){
        String words=spoken==null?"":spoken.trim();
        if(words.isEmpty())throw new IllegalArgumentException("No speech was recognised");
        if(bodyEdit==null)throw new IllegalStateException("Note editor is unavailable");
        Editable editor=bodyEdit.getText();
        int length=editor.length();
        int start=dictatedSelectionStart<0?length:Math.min(dictatedSelectionStart,length);
        int end=dictatedSelectionEnd<0?start:Math.min(dictatedSelectionEnd,length);
        if(end<start){int old=start;start=end;end=old;}
        String before=start>0 && !Character.isWhitespace(editor.charAt(start-1))?" ":"";
        String after=end<length && !Character.isWhitespace(editor.charAt(end))?" ":"";
        String inserted=before+words+after;
        editor.replace(start,end,inserted);
        int caret=start+inserted.length();
        bodyEdit.setSelection(Math.min(caret,editor.length()));
        dictatedSelectionStart=-1;
        dictatedSelectionEnd=-1;
        // Text first, then derived title and best-matching category.
        saveEdits();
        db.applyDictationMetadata(noteId,words);
        Note updated=db.read(noteId);
        if(updated!=null){
            if(titleEdit!=null&&!updated.title.equals(titleEdit.getText().toString()))
                titleEdit.setText(updated.title);
            if(folderNameView!=null)folderNameView.setText("Folder: "+updated.folder);
        }
        saveEdits();
    }
    private void endAndTranscribe(){
        saveEdits();
        if(liveRecording && liveTranscriber!=null){
            liveRecording=false;
            transcriptionRunning=true;
            handler.removeCallbacks(timer);
            updateRecordingButtons();
            if(status!=null)status.setText("Saving your spoken text...");
            liveTranscriber.stop();
            return;
        }
        if(recorder==null||!recorder.isRecording())return;
        try{recordingFile=recorder.stop();recorder=null;handler.removeCallbacks(timer);retry();}
        catch(Exception e){status.setText("Recording error. Any audio already saved is retained.");showError("Recording stopped",e.getMessage());}
        updateRecordingButtons();
    }
    private void retry(){Note n=db.read(noteId);if(n==null||n.audioPath.isEmpty())return;
        File file=new File(n.audioPath);if(!file.exists()){showError("Audio unavailable","Recording file is missing");return;}
        if(transcriptionRunning)return;transcriptionRunning=true;status.setText("Transcribing recording...");updateRecordingButtons();
        transcriber=new PcmTranscriber();transcriber.start(this,file,new PcmTranscriber.Callback(){
            @Override public void success(String text){runOnUiThread(()->{
                try{db.appendTranscript(noteId,text);transcriptionRunning=false;transcriber=null;render();askDeleteAudio(exitAfterTranscript);}
                catch(Exception e){transcriptionRunning=false;status.setText("Text could not be saved. Recording kept for retry.");showError("Save failed",e.getMessage());}
            });}
            @Override public void failure(String reason){runOnUiThread(()->{transcriptionRunning=false;transcriber=null;render();status.setText("Transcription failed. Audio kept. Retry when ready.");showError("Transcription failed",reason);});}
        });
    }
    private void askDeleteAudio(boolean leaveAfter){new AlertDialog.Builder(this).setTitle("Transcription saved").setMessage("Delete the audio recording? The saved text will remain.")
        .setNegativeButton("Keep Audio",(d,w)->{db.keepAudio(noteId);if(leaveAfter)finish();else render();})
        .setPositiveButton("Delete Audio",(d,w)->{boolean deleted=db.deleteAudio(noteId,recordingDir);Toast.makeText(this,deleted?"Audio deleted":"Audio could not be deleted",Toast.LENGTH_LONG).show();if(leaveAfter)finish();else render();})
        .setCancelable(false).show();}
    private void showError(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message==null?"Unknown error":message).setPositiveButton("OK",null).show();}
    private void leave(){saveEdits();if(liveRecording||(recorder!=null&&recorder.isRecording())){exitAfterTranscript=true;endAndTranscribe();}else if(transcriptionRunning){exitAfterTranscript=true;Toast.makeText(this,"Transcription is finishing",Toast.LENGTH_SHORT).show();}else finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onPause(){
        // Moving away from the editor stops recording; saves typed text.
        // Stop here rather than waiting for onStop, which may be delayed.
        saveEdits();
        if(liveRecording||(recorder!=null&&recorder.isRecording())){
            exitAfterTranscript=false;
            endAndTranscribe();
        }
        super.onPause();
    }
    @Override protected void onStop(){
        saveEdits();
        if(liveRecording||(recorder!=null&&recorder.isRecording())){
            exitAfterTranscript=false;
            endAndTranscribe();
        }
        super.onStop();
    }
    @Override protected void onDestroy(){
        saveEdits();
        handler.removeCallbacks(timer);
        handler.removeCallbacks(autosave);
        if(liveTranscriber!=null){
            // Android may destroy an activity while recognition is still active.
            // Save any buffered words before releasing the recognizer.
            if(liveRecording || transcriptionRunning){
                // If Android destroys the editor while finishing, persist the
                // recogniser's buffered words instead of silently losing them.
                String captured=liveTranscriber.snapshot();
                if(!captured.isEmpty())try{db.appendLiveTranscript(noteId,captured);}catch(Exception ignored){}
            }
            liveTranscriber.close();
            liveTranscriber=null;
        }
        if(recorder!=null)recorder.release();
        if(transcriber!=null)transcriber.close();
        if(db!=null)db.close();
        super.onDestroy();
    }
}
