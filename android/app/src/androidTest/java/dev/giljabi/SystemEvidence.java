package dev.giljabi;

import android.app.UiAutomation;
import android.os.ParcelFileDescriptor;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;

/** Preserve test evidence outside app data before the runner cleans up its installed APKs. */
final class SystemEvidence {
    static void preserve(File file)throws IOException{
        if(!file.getName().matches("[a-z0-9.-]+")||file.length()==0)throw new IOException("Invalid test evidence file");
        UiAutomation shell=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        ParcelFileDescriptor[] pipe=shell.executeShellCommandRw("sh -c 'cat > /data/local/tmp/giljabi-"+file.getName()+"'");
        try(InputStream source=new FileInputStream(file);OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
            byte[] buffer=new byte[8192];for(int n;(n=source.read(buffer))!=-1;)output.write(buffer,0,n);
        }
        try(InputStream done=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])){while(done.read()!=-1){}}
    }
}
