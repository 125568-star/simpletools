package com.myapps.mydictation;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.io.File;
import java.util.*;

final class Database extends SQLiteOpenHelper {
    static final String UNFILED="Unfiled";
    Database(Context c){super(c,"my_dictation.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE folders (name TEXT PRIMARY KEY COLLATE NOCASE)");
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL,body TEXT NOT NULL DEFAULT '',kind TEXT NOT NULL DEFAULT 'note',folder TEXT NOT NULL DEFAULT 'Unfiled',color INTEGER NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,audio_path TEXT NOT NULL DEFAULT '',audio_state TEXT NOT NULL DEFAULT 'NONE')");
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT,note_id INTEGER NOT NULL,text TEXT NOT NULL,checked INTEGER NOT NULL DEFAULT 0,position INTEGER NOT NULL,FOREIGN KEY(note_id) REFERENCES notes(id))");
        for(String n:new String[]{UNFILED,"Business Ideas","Shopping","To-do","Reminders"})addFolder(db,n);
    }
    @Override public void onUpgrade(SQLiteDatabase db,int from,int to){}
    void addFolder(String name){if(name!=null&&!name.trim().isEmpty())addFolder(getWritableDatabase(),name.trim());}
    private void addFolder(SQLiteDatabase db,String name){ContentValues v=new ContentValues();v.put("name",name);db.insertWithOnConflict("folders",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    List<String> folders(){List<String> names=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT name FROM folders ORDER BY CASE WHEN name='Unfiled' THEN 1 ELSE 0 END,name COLLATE NOCASE",null)){while(c.moveToNext())names.add(c.getString(0));}return names;}
    Map<String,Integer> counts(){Map<String,Integer> result=new HashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT folder,COUNT(*) FROM notes GROUP BY folder",null)){while(c.moveToNext())result.put(c.getString(0),c.getInt(1));}return result;}
    int totalCount(){try(Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM notes",null)){return c.moveToFirst()?c.getInt(0):0;}}
    long newNote(String kind){long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("title",kind.equals("note")?"New Note":kind.equals("todo")?"To-do List":"Shopping List");v.put("kind",kind);v.put("body","");v.put("folder",kind.equals("shopping")?"Shopping":kind.equals("todo")?"To-do":UNFILED);v.put("color",Ui.CARD);v.put("created_at",now);v.put("updated_at",now);return getWritableDatabase().insertOrThrow("notes",null,v);}
    Note read(long id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,body,kind,folder,color,created_at,updated_at,audio_path,audio_state FROM notes WHERE id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?fromCursor(c):null;}}
    private Note fromCursor(Cursor c){Note n=new Note();n.id=c.getLong(0);n.title=c.getString(1);n.body=c.getString(2);n.kind=c.getString(3);n.folder=c.getString(4);n.color=c.getInt(5);n.createdAt=c.getLong(6);n.updatedAt=c.getLong(7);n.audioPath=c.getString(8);n.audioState=c.getString(9);return n;}
    List<Note> notes(String folder){List<Note> result=new ArrayList<>();String where="All Notes".equals(folder)?"":" WHERE folder=?";String[] args="All Notes".equals(folder)?null:new String[]{folder};try(Cursor c=getReadableDatabase().rawQuery("SELECT id,title,body,kind,folder,color,created_at,updated_at,audio_path,audio_state FROM notes"+where+" ORDER BY updated_at DESC",args)){while(c.moveToNext())result.add(fromCursor(c));}return result;}
    void update(long id,String title,String body){ContentValues v=new ContentValues();v.put("title",title.trim().isEmpty()?"Untitled":title.trim());v.put("body",body);v.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});}
    void setFolder(long id,String folder){addFolder(folder);ContentValues v=new ContentValues();v.put("folder",folder);v.put("updated_at",System.currentTimeMillis());getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});}
    void setColor(long id,int color){ContentValues v=new ContentValues();v.put("color",color);getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});}
    void renameFolder(String oldName,String newName){if(UNFILED.equals(oldName)||newName==null||newName.trim().isEmpty())return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{addFolder(db,newName.trim());ContentValues v=new ContentValues();v.put("folder",newName.trim());db.update("notes",v,"folder=?",new String[]{oldName});db.delete("folders","name=?",new String[]{oldName});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    void removeFolder(String name){if(UNFILED.equals(name))return;SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("folder",UNFILED);db.update("notes",v,"folder=?",new String[]{name});db.delete("folders","name=?",new String[]{name});db.setTransactionSuccessful();}finally{db.endTransaction();}}
    // Move multiple notes atomically. The All Notes view is never a destination.
    int moveNotes(Collection<Long> ids,String folder){
        if(ids==null||ids.isEmpty())return 0;
        if(folder==null||folder.trim().isEmpty()||"All Notes".equalsIgnoreCase(folder.trim()))
            throw new IllegalArgumentException("Choose a real destination folder");
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        int moved=0;
        try{
            addFolder(database,folder.trim());
            long now=System.currentTimeMillis();
            for(Long id:new LinkedHashSet<>(ids)){
                if(id==null)continue;
                ContentValues v=new ContentValues();
                v.put("folder",folder.trim());
                v.put("updated_at",now);
                moved+=database.update("notes",v,"id=?",new String[]{String.valueOf(id)});
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
        return moved;
    }

    // Deleting notes is an explicit confirmed user action. Remove their
    // checklist entries in the same transaction, and only remove audio files
    // from the app-owned recordings directory after the database succeeds.
    int deleteNotes(Collection<Long> ids,File recordingsDir){
        if(ids==null||ids.isEmpty())return 0;
        SQLiteDatabase database=getWritableDatabase();
        List<String> audioPaths=new ArrayList<>();
        int deleted=0;
        database.beginTransaction();
        try{
            for(Long id:new LinkedHashSet<>(ids)){
                if(id==null)continue;
                Note existing=read(id);
                if(existing==null)continue;
                if(existing.audioPath!=null&&!existing.audioPath.isEmpty())
                    audioPaths.add(existing.audioPath);
                String[] args=new String[]{String.valueOf(id)};
                database.delete("items","note_id=?",args);
                deleted+=database.delete("notes","id=?",args);
            }
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
        if(recordingsDir!=null){
            try{
                File safeDir=recordingsDir.getCanonicalFile();
                for(String path:audioPaths){
                    try{
                        File file=new File(path).getCanonicalFile();
                        if(file.getParentFile()!=null && file.getParentFile().equals(safeDir))
                            file.delete();
                    }catch(java.io.IOException ignored){}
                }
            }catch(java.io.IOException ignored){}
        }
        return deleted;
    }

    void setRecording(long id,String audioPath){ContentValues v=new ContentValues();v.put("audio_path",audioPath);v.put("audio_state","PENDING");getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});}
    /**
     * Derive a title, note kind and destination folder from newly dictated words.
     * The editor inserts the text at its own caret and saves the complete body;
     * this method must NOT append that text a second time.
     */
    void applyDictationMetadata(long id,String speech){
        if(speech==null||speech.trim().isEmpty())return;
        SQLiteDatabase database=getWritableDatabase();
        database.beginTransaction();
        try{
            Note n=read(id);
            if(n==null)throw new IllegalStateException("Note is missing");
            String kind=NoteClassifier.kind(speech,n.kind);
            String folder=NoteClassifier.folder(speech,kind);
            if(UNFILED.equals(folder))
                folder=NoteClassifier.matchExistingFolder(speech,folders());
            // Explicitly selected folders win over an ambiguous classification.
            if(UNFILED.equals(folder)&&!UNFILED.equals(n.folder))folder=n.folder;
            addFolder(database,folder);
            ContentValues values=new ContentValues();
            values.put("kind",kind);
            values.put("folder",folder);
            if("New Note".equals(n.title) || "To-do List".equals(n.title) ||
                "Shopping List".equals(n.title))
                values.put("title",NoteClassifier.title(speech,kind));
            values.put("updated_at",System.currentTimeMillis());
            if(database.update("notes",values,"id=?",new String[]{String.valueOf(id)})!=1)
                throw new IllegalStateException("Unable to update dictated note");
            database.setTransactionSuccessful();
        }finally{database.endTransaction();}
    }

    // Live microphone dictation has no corresponding audio file. Do not change
    // the state of an older untranscribed PCM recording on the same note.
    void appendLiveTranscript(long id,String text){appendTranscriptInternal(id,text,false);}
    void appendTranscript(long id,String text){appendTranscriptInternal(id,text,true);}
    private void appendTranscriptInternal(long id,String text,boolean fromSavedAudio){
        if(text==null || text.trim().isEmpty())throw new IllegalArgumentException("Empty transcription");
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            Note n=read(id);if(n==null)throw new IllegalStateException("Note missing");
            String newKind=NoteClassifier.kind(text,n.kind);
            String dest=NoteClassifier.folder(text,newKind);
            if(UNFILED.equals(dest)){
                // File by an existing named category when the spoken words clearly
                // reference it. Never invent new folders or force ambiguous notes.
                dest=NoteClassifier.matchExistingFolder(text,folders());
            }
            // Respect folders the user manually selected before transcribing.
            if(UNFILED.equals(dest)&&!UNFILED.equals(n.folder))dest=n.folder;
            addFolder(db,dest);
            ContentValues v=new ContentValues();v.put("kind",newKind);v.put("folder",dest);
            // Generate the title from the dictated words, but never override a
            // title that was already customised by the user.
            if(n.body.trim().isEmpty() &&
               (n.title.equals("New Note") || n.title.equals("To-do List") || n.title.equals("Shopping List")))
                v.put("title",NoteClassifier.title(text,newKind));
            if(!newKind.equals("note")){
                int pos=itemCount(db,id);
                for(String item:ListParser.parse(text)){ContentValues i=new ContentValues();i.put("note_id",id);i.put("text",item);i.put("position",pos++);db.insertOrThrow("items",null,i);}
                v.put("body",n.body);
            } else v.put("body",n.body.isEmpty()?text:n.body+"\n\n"+text);
            if(fromSavedAudio)v.put("audio_state","VERIFIED");
            v.put("updated_at",System.currentTimeMillis());
            if(db.update("notes",v,"id=?",new String[]{String.valueOf(id)})!=1)throw new IllegalStateException("Unable to save transcript");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private int itemCount(SQLiteDatabase db,long id){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM items WHERE note_id=?",new String[]{String.valueOf(id)})){return c.moveToFirst()?c.getInt(0):0;}}
    static final class ChecklistItem {long id;String text;boolean checked;ChecklistItem(long i,String t,boolean c){id=i;text=t;checked=c;}}
    List<ChecklistItem> items(long noteId){List<ChecklistItem> list=new ArrayList<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT id,text,checked FROM items WHERE note_id=? ORDER BY position,id",new String[]{String.valueOf(noteId)})){while(c.moveToNext())list.add(new ChecklistItem(c.getLong(0),c.getString(1),c.getInt(2)!=0));}return list;}
    void addItem(long id,String text){if(text.trim().isEmpty())return;SQLiteDatabase db=getWritableDatabase();ContentValues v=new ContentValues();v.put("note_id",id);v.put("text",text.trim());v.put("position",itemCount(db,id));db.insertOrThrow("items",null,v);}
    void checked(long itemId,boolean checked){ContentValues v=new ContentValues();v.put("checked",checked?1:0);getWritableDatabase().update("items",v,"id=?",new String[]{String.valueOf(itemId)});}
    void renameItem(long itemId,String text){ContentValues v=new ContentValues();v.put("text",text);getWritableDatabase().update("items",v,"id=?",new String[]{String.valueOf(itemId)});}
    void deleteItem(long itemId){getWritableDatabase().delete("items","id=?",new String[]{String.valueOf(itemId)});}
    void moveItem(long noteId,long itemId,int direction){List<ChecklistItem> list=items(noteId);int index=-1;for(int i=0;i<list.size();i++)if(list.get(i).id==itemId)index=i;if(index<0||index+direction<0||index+direction>=list.size())return;Collections.swap(list,index,index+direction);SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(int i=0;i<list.size();i++){ContentValues v=new ContentValues();v.put("position",i);db.update("items",v,"id=?",new String[]{String.valueOf(list.get(i).id)});}db.setTransactionSuccessful();}finally{db.endTransaction();}}
    boolean deleteAudio(long id,File allowedDir){Note n=read(id);if(n==null||!"VERIFIED".equals(n.audioState)||n.audioPath.isEmpty())return false;
        try{File f=new File(n.audioPath).getCanonicalFile();if(!f.getParentFile().equals(allowedDir.getCanonicalFile()))return false;if(f.exists()&&!f.delete())return false;ContentValues v=new ContentValues();v.put("audio_path","");v.put("audio_state","NONE");getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});return true;}catch(Exception e){return false;}
    }
    void keepAudio(long id){Note n=read(id);if(n==null||!"VERIFIED".equals(n.audioState))return;ContentValues v=new ContentValues();v.put("audio_state","KEEP");getWritableDatabase().update("notes",v,"id=?",new String[]{String.valueOf(id)});}
    int cleanVerified(File allowedDir){int deleted=0;for(Note n:notes("All Notes")){if("VERIFIED".equals(n.audioState)&&deleteAudio(n.id,allowedDir))deleted++;}return deleted;}
    boolean importText(String name,String content){if(content==null||content.trim().isEmpty())return false;String title=name==null?"Imported note":name;SQLiteDatabase db=getWritableDatabase();try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM notes WHERE title=? AND body=?",new String[]{title,content})){if(c.moveToFirst()&&c.getInt(0)>0)return false;}long id=newNote("note");update(id,title,content);setFolder(id,NoteClassifier.folder(content,"note"));return true;}
}
