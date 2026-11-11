package net.miotest.client;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Reproduces STOP while an Android binding keeps the VPN Service alive. */
public final class VpnStopSmoke extends Instrumentation {
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 @Override public void onStart(){Bundle result=new Bundle();Context c=getTargetContext();Activity activity=null;boolean bound=false;
  CountDownLatch connected=new CountDownLatch(1);
  ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName n,IBinder b){connected.countDown();}public void onServiceDisconnected(ComponentName n){}};
  try{
   activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   bound=c.bindService(new Intent(c,MioVpnService.class).setAction(VpnService.SERVICE_INTERFACE),connection,Context.BIND_AUTO_CREATE);
   if(!bound||!connected.await(10,TimeUnit.SECONDS))throw new AssertionError("service binding failed");
   // The previous implementation only reset this state from onDestroy, which
   // cannot run while this binding is held. STOP must release state directly.
   for(int attempt=0;attempt<3;attempt++){
    MioVpnService.running=true;
    c.startService(new Intent(c,MioVpnService.class).setAction("STOP"));
    long deadline=SystemClock.elapsedRealtime()+5000;
    while(MioVpnService.running&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(20);
    if(MioVpnService.running)throw new AssertionError("STOP failed while Service remained bound");
    if(!c.getSharedPreferences("mio",Context.MODE_PRIVATE).getString("phase","").equals("idle"))throw new AssertionError("state not reset");
   }
   result.putString("result","PASS: STOP resets bound VPN service immediately; repeated disconnect is idempotent; idle state persisted");finish(-1,result);
  }catch(Throwable e){result.putString("error",e.toString());finish(0,result);}finally{if(bound)c.unbindService(connection);if(activity!=null){final Activity a=activity;runOnMainSync(()->a.finish());}}
 }
}
