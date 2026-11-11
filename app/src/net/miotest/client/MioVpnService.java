package net.miotest.client;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import java.lang.Process;

public final class MioVpnService extends VpnService {
 public static volatile boolean running=false;
 private volatile Process core;
 private ParcelFileDescriptor tun;
 private volatile boolean stopping=false;
 private volatile boolean failed=false;
 private NodeBook.Node node;
 private volatile int generation=0;
 private File activeConfig,activeCa;
 private StringBuilder log=new StringBuilder();
 private SharedPreferences prefs;
 private final Handler logHandler=new Handler(Looper.getMainLooper());
 private boolean flushPending=false;
 @Override public void onCreate(){super.onCreate();prefs=getSharedPreferences("mio",MODE_PRIVATE);}
 @Override public int onStartCommand(Intent i,int flags,int id){
  if(i==null || "STOP".equals(i.getAction())){failed=false;shutdown();stopSelf();return START_NOT_STICKY;}
  if(running)return START_NOT_STICKY;
  try{NodeBook b=new NodeStore(this).load();String nodeId=i.getStringExtra("node_id");node=nodeId==null?b.selected():b.get(nodeId);if(node==null)throw new IOException("请先添加并选择节点");}catch(Exception e){prefs.edit().putString("phase","idle").putString("error","无法读取节点："+e.getMessage()).apply();failed=true;stopSelf();return START_NOT_STICKY;}
  running=true;stopping=false;failed=false;
  final int token=++generation;final NodeBook.Node selectedNode=node;
  prefs.edit().putString("active_id",node.id).putLong("active_revision",node.revision).putString("active_name",node.name).putString("phase","starting").putString("error","").putString("exit_ip","").putLong("started_elapsed",SystemClock.elapsedRealtime()).apply();
  NotificationManager nm=getSystemService(NotificationManager.class);
  nm.createNotificationChannel(new NotificationChannel("mio","pre-smio VPN",NotificationManager.IMPORTANCE_LOW));
  startForeground(1,notification("正在启动"));status("正在启动");
  new Thread(()->startTunnel(selectedNode,token),"mio-start").start();return START_NOT_STICKY;
 }
 private Notification notification(String text){
  PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  Intent stop=new Intent(this,MioVpnService.class).setAction("STOP");
  PendingIntent sp=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  return new Notification.Builder(this,"mio").setContentTitle(node==null?"pre-smio":node.name+" · pre-smio").setContentText(text).setSmallIcon(R.drawable.ic_notification).setContentIntent(pi).setOngoing(true).addAction(new Notification.Action.Builder(null,"断开",sp).build()).build();
 }
 private void status(String s){prefs.edit().putString("status",s).apply();}
 private synchronized void append(String s){
  // Passwords are never written by this wrapper.
  log.append(s).append('\n');if(log.length()>24000)log.delete(0,log.length()-20000);
  if(!flushPending){flushPending=true;logHandler.postDelayed(()->flushLog(),250);}
 }
 private synchronized void flushLog(){flushPending=false;try{Files.write(new File(getFilesDir(),"core.log").toPath(),log.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
 private void fail(String message){failed=true;prefs.edit().putString("error",message).apply();status(message);}
 private boolean current(int token){return !stopping&&generation==token;}
 private void stopFailed(int token,String message){logHandler.post(()->{if(!current(token))return;fail(message);shutdown();stopSelf();});}
 private void startTunnel(NodeBook.Node node,int token){
  try{
   synchronized(this){if(!current(token))return;log.setLength(0);}append("启动 pre-smio · "+node.name);
   String socket="mio-test-"+UUID.randomUUID();
   String server=java.net.InetAddress.getByName(node.server).getHostAddress();
   int socksPort;try(java.net.ServerSocket probe=new java.net.ServerSocket(0,1,java.net.InetAddress.getByName("127.0.0.1"))){socksPort=probe.getLocalPort();}
   JSONObject cfg=new JSONObject();cfg.put("Server",server);cfg.put("Port",node.port);cfg.put("Dest",node.dest);cfg.put("DestPort",node.destPort);cfg.put("Password",node.password);cfg.put("Socket",socket);cfg.put("SocksPort",socksPort);
   File config=new File(getFilesDir(),"connection-"+socket+".json");Files.write(config.toPath(),cfg.toString().getBytes(StandardCharsets.UTF_8));
   File ca=new File(getFilesDir(),"ca-"+socket+".pem");
   synchronized(this){if(!current(token)){config.delete();return;}activeConfig=config;activeCa=ca;}
   javax.net.ssl.TrustManagerFactory tmf=javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());tmf.init((java.security.KeyStore)null);
   StringBuilder pem=new StringBuilder();
   for(javax.net.ssl.TrustManager tm:tmf.getTrustManagers())if(tm instanceof javax.net.ssl.X509TrustManager)for(java.security.cert.X509Certificate cert:((javax.net.ssl.X509TrustManager)tm).getAcceptedIssuers())pem.append("-----BEGIN CERTIFICATE-----\n").append(android.util.Base64.encodeToString(cert.getEncoded(),android.util.Base64.NO_WRAP)).append("\n-----END CERTIFICATE-----\n");
   Files.write(ca.toPath(),pem.toString().getBytes(StandardCharsets.US_ASCII));
   ProcessBuilder pb=new ProcessBuilder(new File(getApplicationInfo().nativeLibraryDir,"libmiocore.so").getAbsolutePath(),config.getAbsolutePath()).redirectErrorStream(true);pb.environment().put("SSL_CERT_FILE",ca.getAbsolutePath());
   Process p=pb.start();
   synchronized(this){if(!current(token)){p.destroyForcibly();config.delete();ca.delete();return;}core=p;}
   new Thread(()->readCore(p,token),"mio-log").start();
   LocalSocket ipc=null;
   for(int attempt=0;attempt<100&&current(token);attempt++){
    LocalSocket candidate=new LocalSocket();
    try{candidate.connect(new LocalSocketAddress(socket,LocalSocketAddress.Namespace.ABSTRACT));ipc=candidate;break;}catch(IOException e){candidate.close();if(!p.isAlive())throw new IOException("mio 核心已退出，请查看日志");Thread.sleep(100);}
   }
   if(!current(token)){if(ipc!=null)ipc.close();return;}
   if(ipc==null)throw new IOException("等待核心启动超时");
   try(LocalSocket channel=ipc){
    Builder b=new Builder().setSession(node.name+" · pre-smio").setMtu(1280).addAddress("10.77.0.2",32).addAddress("fd77::2",128).addRoute("0.0.0.0",0).addRoute("::",0).addDnsServer("1.1.1.1");
    // Excluding our own UID also excludes the child Go process, preventing
    // recursive routing of the mio TCP and QUIC transport connections.
    b.addDisallowedApplication(getPackageName());b.setBlocking(true);
    if(Build.VERSION.SDK_INT>=29)b.setMetered(false);
    ParcelFileDescriptor fd;
    synchronized(this){if(!current(token))return;fd=b.establish();if(fd==null)throw new IOException("VPN 授权已失效");tun=fd;}
    channel.setFileDescriptorsForSend(new FileDescriptor[]{fd.getFileDescriptor()});channel.getOutputStream().write(1);channel.getOutputStream().flush();
   }
  }catch(Exception e){if(current(token)){append("启动失败："+e.getMessage());stopFailed(token,"启动失败："+e.getMessage());}}
 }
 private void readCore(Process p,int token){
  try(BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))){
   String s;while((s=r.readLine())!=null){synchronized(this){if(!current(token))break;append(s);if(s.contains("MIO_VPN_READY")){prefs.edit().putString("phase","ready").apply();status("VPN 已启动，等待联网测试");getSystemService(NotificationManager.class).notify(1,notification("正在验证连接"));}if(s.startsWith("MIO_EXIT_IP:")){String ip=s.substring(12).trim();prefs.edit().putString("phase","connected").putString("exit_ip",ip).putString("error","").apply();status("已连接 · "+ip);getSystemService(NotificationManager.class).notify(1,notification("已连接 · "+ip));}if(s.contains("出口 IP 测试失败")||s.contains("出口 IP 测试未通过")){prefs.edit().putString("phase","unverified").putString("error","出口检测未通过，请查看运行日志").apply();getSystemService(NotificationManager.class).notify(1,notification("连接待验证"));}}}
   int code=p.waitFor();if(current(token)){append("核心退出，代码："+code);stopFailed(token,"核心已退出，请查看运行日志");}
  }catch(Exception e){if(current(token)){append("核心日志错误："+e.getMessage());stopFailed(token,"无法读取核心状态");}}
 }
 private void shutdown(){
  Process oldCore;
  synchronized(this){
   stopping=true;generation++;running=false;oldCore=core;core=null;
   if(tun!=null){try{tun.close();}catch(IOException ignored){}tun=null;}
   if(activeConfig!=null){activeConfig.delete();activeConfig=null;}
   if(activeCa!=null){activeCa.delete();activeCa=null;}
  }
  if(oldCore!=null){oldCore.destroy();new Thread(()->{try{if(!oldCore.waitFor(500,java.util.concurrent.TimeUnit.MILLISECONDS))oldCore.destroyForcibly();}catch(InterruptedException e){oldCore.destroyForcibly();Thread.currentThread().interrupt();}},"mio-stop").start();}
  prefs.edit().putString("phase","idle").putString("exit_ip","").apply();if(!failed){prefs.edit().putString("error","").apply();status("已断开");}
  logHandler.removeCallbacksAndMessages(null);flushLog();stopForeground(STOP_FOREGROUND_REMOVE);
 }
 @Override public void onRevoke(){failed=false;shutdown();stopSelf();super.onRevoke();}
 @Override public void onDestroy(){shutdown();super.onDestroy();}
}
