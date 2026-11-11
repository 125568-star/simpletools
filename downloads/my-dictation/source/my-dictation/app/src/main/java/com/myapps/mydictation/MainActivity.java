package com.myapps.mydictation;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MainActivity extends Activity {
    private Database db;
    private String openedFolder=null;
    private LinearLayout content;
    private final Set<Long> selectedNoteIds=new LinkedHashSet<>();
    private final Map<Long,Note> listNotes=new LinkedHashMap<>();
    private final Map<Long,LinearLayout> listCards=new LinkedHashMap<>();
    private LinearLayout selectionActions;
    private TextView selectionCount;
    private static final int IMPORT_FILE=117;

    @Override public void onCreate(Bundle state){super.onCreate(state);db=new Database(this);showHome();}
    @Override protected void onResume(){super.onResume();if(db!=null){if(openedFolder==null)showHome();else showFolder(openedFolder);}}
    private void screen(String title){
        LinearLayout base=Ui.vertical(this);base.setBackgroundColor(Ui.BG);
        LinearLayout top=Ui.horizontal(this);Ui.pad(top,this,2);
        TextView heading=Ui.heading(this,title);top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        Button plus=Ui.button(this,"+  New");top.addView(plus);plus.setOnClickListener(v->chooseType());
        Ui.add(base,top,3);
        ScrollView scroll=new ScrollView(this);content=Ui.vertical(this);Ui.pad(content,this,4);scroll.addView(content);base.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Ui.pad(base,this,14);setContentView(base);
    }
    private void showHome(){
        selectedNoteIds.clear();listNotes.clear();listCards.clear();
        selectionActions=null;selectionCount=null;
        openedFolder=null;screen("My Dictation");
        TextView subtitle=Ui.text(this,"Folders",17);subtitle.setTextColor(Ui.MUTED);Ui.add(content,subtitle,12);
        Map<String,Integer> counts=db.counts();
        for(String name:db.folders()){
            if(!Database.UNFILED.equals(name) && !"All Notes".equalsIgnoreCase(name))
                folderCard(name,counts.getOrDefault(name,0),true);
        }
        // The two catch-all views always stay below the regular folders.
        folderCard(Database.UNFILED,counts.getOrDefault(Database.UNFILED,0),false);
        folderCard("All Notes",db.totalCount(),false);
        Button add=Ui.secondary(this,"+ New Folder");Ui.add(content,add,16);add.setOnClickListener(v->askFolderName(null));
        Button importBtn=Ui.secondary(this,"Import exported notes");Ui.add(content,importBtn,6);importBtn.setOnClickListener(v->pickImport());
        Button cleanup=Ui.secondary(this,"Clean up verified audio");Ui.add(content,cleanup,6);cleanup.setOnClickListener(v->confirmCleanup());
        TextView hint=Ui.text(this,"Choose a folder or tap + New to dictate. No tags, no Organize screen.",13);hint.setTextColor(Ui.MUTED);Ui.add(content,hint,14);
    }
    private void folderCard(String name,int count,boolean editable){
        LinearLayout line=Ui.horizontal(this);line.setBackground(Ui.shape(Ui.CARD,this));Ui.pad(line,this,13);
        TextView label=Ui.text(this,"▣   "+name+" ("+count+")",17);line.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        if(editable&&!Database.UNFILED.equals(name)){
            Button menu=Ui.secondary(this,"⋮");line.addView(menu);menu.setOnClickListener(v->folderActions(name));
        }
        Ui.add(content,line,10);line.setOnClickListener(v->showFolder(name));
    }
    private void showFolder(String name){
        openedFolder=name;
        selectedNoteIds.clear();listNotes.clear();listCards.clear();
        screen(name);
        Button back=Ui.secondary(this,"‹ All folders");Ui.add(content,back,6);back.setOnClickListener(v->showHome());
        selectionActions=Ui.horizontal(this);
        selectionActions.setBackground(Ui.shape(Ui.CARD,this));Ui.pad(selectionActions,this,8);
        selectionCount=Ui.text(this,"0 selected",15);
        selectionActions.addView(selectionCount,new LinearLayout.LayoutParams(0,-2,1));
        Button move=Ui.secondary(this,"Move");selectionActions.addView(move);
        move.setOnClickListener(v->moveSelected());
        Button delete=Ui.button(this,"Delete");selectionActions.addView(delete);
        delete.setOnClickListener(v->confirmDeleteSelected());
        Ui.add(content,selectionActions,8);
        List<Note> notes=db.notes(name);
        if(notes.isEmpty()){TextView empty=Ui.text(this,"No notes here yet. Tap + New to create one.",15);empty.setTextColor(Ui.MUTED);Ui.add(content,empty,20);}
        for(Note n:notes){
            LinearLayout card=Ui.vertical(this);card.setBackground(Ui.shape(n.color,this));Ui.pad(card,this,14);
            TextView title=Ui.text(this,n.kind.equals("note")?"✎  "+n.title:"☑  "+n.title,17);title.setTextColor(Ui.foreground(n.color));card.addView(title);
            if(!n.body.isEmpty()){TextView body=Ui.text(this,n.body.replace('\n',' '),13);body.setMaxLines(2);body.setTextColor(Ui.foreground(n.color));Ui.add(card,body,6);}
            if(!n.kind.equals("note")){TextView meta=Ui.text(this,db.items(n.id).size()+" list items",12);Ui.add(card,meta,6);}
            listCards.put(n.id,card);listNotes.put(n.id,n);
            Ui.add(content,card,10);
            card.setOnLongClickListener(v->{toggleSelection(n.id);return true;});
            card.setOnClickListener(v->{
                if(!selectedNoteIds.isEmpty()){toggleSelection(n.id);return;}
                Intent i=new Intent(this,NoteActivity.class);i.putExtra("id",n.id);startActivity(i);
            });
        }
        updateSelectionBar();
    }
    private void toggleSelection(long noteId){
        if(!selectedNoteIds.add(noteId))selectedNoteIds.remove(noteId);
        updateSelectionBar();
    }
    private void updateSelectionBar(){
        if(selectionActions!=null)selectionActions.setVisibility(selectedNoteIds.isEmpty()?View.GONE:View.VISIBLE);
        if(selectionCount!=null)selectionCount.setText(selectedNoteIds.size()+" selected");
        for(Map.Entry<Long,LinearLayout> e:listCards.entrySet()){
            Note n=listNotes.get(e.getKey());
            if(n==null)continue;
            android.graphics.drawable.GradientDrawable bg=Ui.shape(n.color,this);
            if(selectedNoteIds.contains(e.getKey()))bg.setStroke(Ui.dp(this,3),Ui.PRIMARY);
            e.getValue().setBackground(bg);
            e.getValue().setSelected(selectedNoteIds.contains(e.getKey()));
        }
    }
    private void moveSelected(){
        if(selectedNoteIds.isEmpty())return;
        ArrayList<String> folders=new ArrayList<>();
        for(String folder:db.folders())if(!"All Notes".equalsIgnoreCase(folder))folders.add(folder);
        folders.add("+ New Folder");
        String[] choices=folders.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("Move "+selectedNoteIds.size()+" note(s) to folder")
            .setItems(choices,(dialog,which)->{
                if(which==choices.length-1){
                    EditText name=Ui.edit(this,"Folder name");
                    new AlertDialog.Builder(this).setTitle("Create destination folder")
                        .setView(name).setNegativeButton("Cancel",null)
                        .setPositiveButton("Move",(d,w)->{
                            String folder=name.getText().toString().trim();
                            if(!folder.isEmpty())completeMove(folder);
                        }).show();
                }else completeMove(choices[which]);
            }).setNegativeButton("Cancel",null).show();
    }
    private void completeMove(String folder){
        try{
            int moved=db.moveNotes(selectedNoteIds,folder);
            selectedNoteIds.clear();
            showFolder(openedFolder);
            Toast.makeText(this,moved+" note(s) moved to "+folder,Toast.LENGTH_SHORT).show();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Move failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    private void confirmDeleteSelected(){
        if(selectedNoteIds.isEmpty())return;
        final int count=selectedNoteIds.size();
        new AlertDialog.Builder(this).setTitle("Delete "+count+" note(s)?")
            .setMessage("This permanently deletes the selected notes and their checklist items. Associated recordings will also be deleted. This cannot be undone.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                try{
                    int deleted=db.deleteNotes(selectedNoteIds,new File(getFilesDir(),"recordings"));
                    selectedNoteIds.clear();
                    showFolder(openedFolder);
                    Toast.makeText(this,deleted+" note(s) deleted",Toast.LENGTH_SHORT).show();
                }catch(Exception e){new AlertDialog.Builder(this).setTitle("Delete failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
            }).show();
    }
    private void chooseType(){
        String[] types={"Record Note","Create To-do","Create List / Shopping"};
        new AlertDialog.Builder(this).setTitle("What would you like to create?").setItems(types,(d,which)->{
            String kind=which==0?"note":which==1?"todo":"shopping";
            long id=db.newNote(kind);
            Intent intent=new Intent(this,NoteActivity.class);intent.putExtra("id",id);// Recording begins only after the user taps Record in the editor.startActivity(intent);
        }).show();
    }
    private void askFolderName(String previous){
        EditText input=Ui.edit(this,"Folder name");if(previous!=null)input.setText(previous);
        new AlertDialog.Builder(this).setTitle(previous==null?"New Folder":"Rename Folder").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            String name=input.getText().toString().trim();if(name.isEmpty())return;
            if(previous==null)db.addFolder(name);else db.renameFolder(previous,name);showHome();
        }).show();
    }
    private void folderActions(String name){new AlertDialog.Builder(this).setTitle(name).setItems(new String[]{"Rename","Delete folder (keep notes in Unfiled)"},(d,which)->{
        if(which==0)askFolderName(name);else new AlertDialog.Builder(this).setTitle("Delete folder?").setMessage("Notes will move to Unfiled, not be deleted.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->{db.removeFolder(name);showHome();}).show();
    }).show();}
    private void confirmCleanup(){new AlertDialog.Builder(this).setTitle("Clean up verified recordings?").setMessage("Only recordings flagged as completely transcribed and saved will be deleted. Untranscribed audio and audio you chose to keep remain untouched.").setNegativeButton("Cancel",null).setPositiveButton("Clean up",(d,w)->{
        File dir=new File(getFilesDir(),"recordings");int count=db.cleanVerified(dir);Toast.makeText(this,count+" verified recording(s) deleted",Toast.LENGTH_LONG).show();
    }).show();}
    private void pickImport(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/markdown","application/json","application/octet-stream"});startActivityForResult(i,IMPORT_FILE);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT_FILE||result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            String name="Imported note";try(android.database.Cursor c=getContentResolver().query(data.getData(),new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream stream=getContentResolver().openInputStream(data.getData())){if(stream==null)throw new IOException("Cannot open export");byte[] buf=new byte[8192];int n;while((n=stream.read(buf))!=-1){if(bytes.size()+n>10_000_000)throw new IOException("Export is over 10 MB");bytes.write(buf,0,n);}}
            String body=bytes.toString(StandardCharsets.UTF_8.name());int added=0;
            if(name.toLowerCase(Locale.ROOT).endsWith(".json")){
                Object json=new JSONTokener(body).nextValue();JSONArray arr=json instanceof JSONArray?(JSONArray)json:json instanceof JSONObject?((JSONObject)json).optJSONArray("notes"):null;
                if(arr==null)throw new IOException("Expected a JSON list or {notes:[...]} export");
                for(int i=0;i<arr.length();i++){JSONObject n=arr.optJSONObject(i);if(n==null)continue;String title=n.optString("title","Imported Note"),text=n.optString("body",n.optString("content",n.optString("text","")));if(db.importText(title,text))added++;}
            }else if(db.importText(name.replaceFirst("\\.(txt|md)$",""),body))added++;
            Toast.makeText(this,added+" note(s) imported",Toast.LENGTH_LONG).show();showHome();
        }catch(Exception e){new AlertDialog.Builder(this).setTitle("Import failed").setMessage(e.getMessage()).setPositiveButton("OK",null).show();}
    }
    @Override public void onBackPressed(){
        if(!selectedNoteIds.isEmpty()){selectedNoteIds.clear();updateSelectionBar();}
        else if(openedFolder!=null)showHome();
        else super.onBackPressed();
    }
}
