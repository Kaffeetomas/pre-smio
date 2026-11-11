package net.miotest.client;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

/** Emulator-only harness; never included in the delivered release APK. */
public final class AndroidSmoke extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 @Override public void onStart(){Bundle result=new Bundle();try{
  Context c=getTargetContext();NodeStore store=new NodeStore(c);NodeBook b=store.load();check(!b.nodes().isEmpty(),"legacy migration missing");
  NodeBook.Node n=NodeBook.Node.create("Tokyo-02","198.51.100.2",8443,"one.one.one.one",443,"ANDROID_SMOKE_DUMMY_SECRET");b.put(n);b.select(n.id);store.save(b);
  NodeBook loaded=new NodeStore(c).load();check(loaded.selected().id.equals(n.id),"selected node not persisted");check(loaded.get(n.id).password.equals(n.password),"credential not restored");
  NodeBook.Node edit=NodeBook.Node.create("Tokyo · Personal","198.51.100.3",9443,"example.com",443,"EDITED_DUMMY_SECRET");edit.id=n.id;edit.revision=n.revision+1;loaded.put(edit);store.save(loaded);loaded=store.load();check(loaded.selected().server.equals("198.51.100.3"),"edit not persisted");
  NodeBook.Node copy=NodeBook.Node.create("Disposable copy",edit.server,edit.port,edit.dest,edit.destPort,edit.password);loaded.put(copy);store.save(loaded);loaded=store.load();check(loaded.get(copy.id)!=null,"copy missing");loaded.remove(copy.id);store.save(loaded);check(store.load().get(copy.id)==null,"delete not persisted");
  File f=new File(c.getFilesDir(),"nodes.enc");byte[] original=Files.readAllBytes(f.toPath());check(!new String(original,StandardCharsets.ISO_8859_1).contains("EDITED_DUMMY_SECRET"),"plaintext secret on disk");
  byte[] corrupt=original.clone();corrupt[corrupt.length-1]^=1;try{Files.write(f.toPath(),corrupt);try{store.load();throw new AssertionError("tampered ciphertext accepted");}catch(javax.crypto.AEADBadTagException expected){}check(Arrays.equals(Files.readAllBytes(f.toPath()),corrupt),"failed read overwrote storage");}finally{Files.write(f.toPath(),original);}
  check(store.load().selected().id.equals(n.id),"restore failed");result.putString("result","PASS: encrypted multi-node add/edit/copy/delete, selection persistence, credential roundtrip, AES-GCM tamper rejection without data reset");finish(-1,result);
 }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}}
}
