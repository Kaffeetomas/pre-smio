package net.miotest.client;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class NodeStore {
 private static final String KEY="mio.nodes.v1";
 private final Context context;
 private final AtomicFile file;
 public NodeStore(Context c){context=c.getApplicationContext();file=new AtomicFile(new File(context.getFilesDir(),"nodes.enc"));}
 private SecretKey key()throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(ks.containsAlias(KEY))return (SecretKey)ks.getKey(KEY,null);
  KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
  generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return generator.generateKey();
 }
 public synchronized NodeBook load()throws Exception {
  if(!file.getBaseFile().exists()&&!new File(file.getBaseFile().getPath()+".bak").exists())return migrate();
  byte[] data=file.readFully();if(data.length<30||data[0]!=1)throw new IOException("节点文件格式不正确");
  int size=data[1]&255;if(size!=12||data.length<2+size+16)throw new IOException("节点文件已损坏");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,data,2,size));
  return NodeBook.decode(new String(cipher.doFinal(data,2+size,data.length-2-size),StandardCharsets.UTF_8));
 }
 public synchronized void save(NodeBook b)throws Exception {
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] iv=cipher.getIV();byte[] encrypted=cipher.doFinal(b.encode().getBytes(StandardCharsets.UTF_8));
  FileOutputStream stream=null;try{stream=file.startWrite();stream.write(1);stream.write(iv.length);stream.write(iv);stream.write(encrypted);file.finishWrite(stream);}catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
 }
 private NodeBook migrate()throws Exception {
  SharedPreferences p=context.getSharedPreferences("mio",Context.MODE_PRIVATE);NodeBook b=new NodeBook();
  if(!p.getString("password","").isEmpty()){
   NodeBook.Node n=NodeBook.Node.create("我的 VPS",p.getString("server",""),Integer.parseInt(p.getString("port","8443")),p.getString("dest","one.one.one.one"),443,p.getString("password",""));b.put(n);
  }
  save(b);
  // Remove the old plaintext fields only after encrypted persistence succeeds.
  p.edit().remove("password").remove("server").remove("port").remove("dest").commit();return b;
 }
}
