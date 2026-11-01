package dev.giljabi;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/** Private, bounded diagnostics. Nothing is uploaded; sharing requires the copy button. */
final class CrashDiagnostics {
    private static volatile Context application;
    private static volatile String currentStage="앱 초기화",previousStage="기록 없음";
    private static final AtomicLong lastSaved=new AtomicLong(-10000);
    private static boolean installed;
    private static final int LIMIT=24000;
    static synchronized void install(Context context){
        application=context.getApplicationContext();if(installed)return;installed=true;
        SharedPreferences prefs=application.getSharedPreferences("diagnostics",Context.MODE_PRIVATE);
        previousStage=prefs.getString("stage","기록 없음")+" ("+prefs.getString("version","알 수 없는 버전")+")";
        Thread.UncaughtExceptionHandler original=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{record("처리되지 않은 오류 · "+thread.getName(),error);}catch(Throwable ignored){}
            if(original!=null)original.uncaughtException(thread,error);else{Process.killProcess(Process.myPid());System.exit(10);}
        });
        checkpoint("앱 시작");
    }
    static void stage(String stage){
        currentStage=stage;Context context=application;if(context==null)return;
        long now=SystemClock.elapsedRealtime(),before=lastSaved.get();
        if(now-before>=5000&&lastSaved.compareAndSet(before,now))saveStage(context,stage,false);
    }
    static void checkpoint(String stage){currentStage=stage;Context context=application;if(context!=null){lastSaved.set(SystemClock.elapsedRealtime());saveStage(context,stage,true);}}
    private static void saveStage(Context context,String stage,boolean synchronous){
        SharedPreferences.Editor editor=context.getSharedPreferences("diagnostics",Context.MODE_PRIVATE).edit().putString("stage",stage).putString("version",BuildConfig.VERSION_NAME);
        if(synchronous)editor.commit();else editor.apply();
    }
    static synchronized void record(String phase,Throwable error){
        Context context=application;if(context==null)return;
        try{
            StringWriter stack=new StringWriter();error.printStackTrace(new PrintWriter(stack));
            String report=DateFormat.getDateTimeInstance().format(new Date())+" · "+BuildConfig.VERSION_NAME+"\n"+phase+"\n단계: "+currentStage+"\n"+stack;
            if(report.length()>LIMIT)report=report.substring(0,LIMIT)+"\n[이후 생략]";
            report=report.replaceAll("AIza[0-9A-Za-z_-]{20,}","[API key removed]");
            AtomicFile file=new AtomicFile(new File(context.getNoBackupFilesDir(),"last-failure.txt"));FileOutputStream output=null;
            try{output=file.startWrite();output.write(report.getBytes(StandardCharsets.UTF_8));file.finishWrite(output);}catch(Throwable failure){if(output!=null)file.failWrite(output);}
        }catch(Throwable ignored){}
    }
    static String report(Context context){
        StringBuilder out=new StringBuilder("길잡이 ").append(BuildConfig.VERSION_NAME).append("\n기기: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append("\nAndroid ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n이전 실행 마지막 저장 단계: ").append(previousStage).append("\n현재 단계: ").append(currentStage).append("\n");
        ActivityManager manager=context.getSystemService(ActivityManager.class);
        if(manager!=null){ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();manager.getMemoryInfo(memory);out.append("시스템 여유 메모리: ").append(memory.availMem/1048576).append(" MiB / ").append(memory.totalMem/1048576).append(" MiB\n");}
        out.append("현재 앱 native heap: ").append(Debug.getNativeHeapAllocatedSize()/1048576).append(" MiB\n\n마지막 Java/영상 오류:\n");
        File saved=new File(context.getNoBackupFilesDir(),"last-failure.txt");
        try(InputStream in=new FileInputStream(saved)){byte[] bytes=new byte[LIMIT*4];int count=0,n;while(count<bytes.length&&(n=in.read(bytes,count,bytes.length-count))!=-1)count+=n;out.append(new String(bytes,0,count,StandardCharsets.UTF_8));}
        catch(IOException failure){out.append("저장된 오류 없음. 기존 버전에서 발생한 Java 오류는 이 기록에 없을 수 있습니다.");}
        if(Build.VERSION.SDK_INT>=30&&manager!=null){
            out.append("\n\nAndroid가 기록한 최근 종료 이유:\n");
            try{
                java.util.List<ApplicationExitInfo> exits=manager.getHistoricalProcessExitReasons(context.getPackageName(),0,3);
                if(exits.isEmpty())out.append("기록 없음\n");
                for(ApplicationExitInfo exit:exits)out.append(DateFormat.getDateTimeInstance().format(new Date(exit.getTimestamp()))).append(" · ").append(reason(exit.getReason())).append(" (code ").append(exit.getReason()).append(") · status ").append(exit.getStatus()).append("\nPSS ").append(exit.getPss()/1024).append(" MiB / RSS ").append(exit.getRss()/1024).append(" MiB\n").append(exit.getDescription()).append('\n');
            }catch(RuntimeException failure){out.append("종료 기록을 읽지 못했습니다.\n");}
        }
        out.append("\n오류 기록은 자동 전송하지 않습니다. 복사한 내용을 확인하고 공유하세요.");return out.toString();
    }
    private static String reason(int reason){
        switch(reason){case ApplicationExitInfo.REASON_LOW_MEMORY:return "시스템 메모리 부족 종료";case ApplicationExitInfo.REASON_CRASH:return "Java 오류 종료";case ApplicationExitInfo.REASON_CRASH_NATIVE:return "네이티브 오류 종료";case ApplicationExitInfo.REASON_ANR:return "응답 없음(ANR)";case ApplicationExitInfo.REASON_USER_REQUESTED:return "사용자/시스템 중지 요청";case ApplicationExitInfo.REASON_SIGNALED:return "프로세스 시그널 종료";default:return "기타 종료";}
    }
}
