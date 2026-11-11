package net.miotest.client;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Record uncaught Java errors, then delegate to Android's normal handler. */
public final class CrashGuard extends Application {
 static void writeReport(Context context,Thread thread,Throwable error)throws Exception {
  StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));
  String report="pre-smio 1.0.3\n"+Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nThread: "+thread.getName()+"\n\n"+stack.toString();
  Files.write(new File(context.getFilesDir(),"last-crash.txt").toPath(),report.getBytes(StandardCharsets.UTF_8));
 }
 @Override public void onCreate(){
  super.onCreate();final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{writeReport(this,thread,error);}catch(Throwable ignored){}
   if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}
  });
 }
}
