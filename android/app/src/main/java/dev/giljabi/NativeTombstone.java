package dev.giljabi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Reads selected fields of AOSP debuggerd/proto/tombstone.proto, not memory or log dumps.
 * Schema: https://android.googlesource.com/platform/system/core/+/refs/heads/main/debuggerd/proto/tombstone.proto
 */
final class NativeTombstone {
    static final int MAX_BYTES=8*1024*1024;
    static String read(InputStream input)throws IOException {
        byte[] raw=limited(input);
        if(raw.length>=2&&(raw[0]&255)==31&&(raw[1]&255)==139)try(InputStream gzip=new GZIPInputStream(new ByteArrayInputStream(raw))){raw=limited(gzip);}
        if(raw.length==0)throw new IOException("상세 추적 파일이 비어 있습니다.");
        if(new String(raw,0,Math.min(raw.length,160),StandardCharsets.UTF_8).contains("*** ***"))return text(raw);
        Reader r=new Reader(raw,0,raw.length);long pid=0,tid=0,page=0;String abort="",signal="";List<Reader> entries=new ArrayList<>();
        while(r.more()){
            int tag=r.tag(),field=tag>>>3,wire=tag&7;
            if(wire==0&&(field==5||field==6||field==22)){long v=r.number();if(field==5)pid=v;else if(field==6)tid=v;else page=v;}
            else if(field==14&&wire==2)abort=r.string();
            else if(field==10&&wire==2)signal=signal(r.block());
            else if(field==16&&wire==2){Reader entry=r.block();if(entries.size()<4096)entries.add(entry);}
            else r.skip(tag);
        }
        StringBuilder out=new StringBuilder("네이티브 상세 기록\npid ").append(pid).append(" / tid ").append(tid).append("\nSignal: ").append(signal).append("\nAbort message: ").append(abort.isEmpty()?"제공되지 않음":abort).append('\n');
        if(page>0)out.append("Page size: ").append(page).append('\n');
        boolean found=false;
        for(Reader entry:entries){long key=0;Reader value=null;
            while(entry.more()){int tag=entry.tag();if(tag==8)key=entry.number();else if(tag==18)value=entry.block();else entry.skip(tag);}
            if(key==tid&&value!=null){thread(value,out);found=true;break;}
        }
        if(!found)out.append("충돌 스레드 호출 기록이 제공되지 않았습니다.\n");
        String report=out.toString().replaceAll("AIza[0-9A-Za-z_-]{20,}","[API key removed]");
        return report.length()>32000?report.substring(0,32000)+"\n[이후 생략]":report;
    }
    private static byte[] limited(InputStream input)throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] block=new byte[8192];int n;
        while((n=input.read(block))!=-1){if(out.size()>MAX_BYTES-n)throw new IOException("상세 추적 파일이 크기 제한을 넘었습니다.");out.write(block,0,n);}return out.toByteArray();
    }
    private static String signal(Reader r)throws IOException {
        long number=0;String name="";
        while(r.more()){int t=r.tag();if(t==8)number=r.number();else if(t==18)name=r.string();else r.skip(t);}
        return number+" "+name;
    }
    private static void thread(Reader r,StringBuilder out)throws IOException {
        String name="";List<String> frames=new ArrayList<>();
        while(r.more()){int t=r.tag();if(t==18)name=r.string();else if(t==34){Reader f=r.block();if(frames.size()<48)frames.add(frame(f,frames.size()));}else r.skip(t);}
        out.append("Thread: ").append(name).append("\nbacktrace:\n");for(String f:frames)out.append(f).append('\n');
    }
    private static String frame(Reader r,int index)throws IOException {
        long pc=0,offset=0;String function="",file="",build="";
        while(r.more()){int t=r.tag();if(t==8)pc=r.number();else if(t==34)function=r.string();else if(t==40)offset=r.number();else if(t==50)file=r.string();else if(t==66)build=r.string();else r.skip(t);}
        return String.format(Locale.ROOT,"#%02d pc %016x %s%s%s",index,pc,file,function.isEmpty()?"":" ("+function+"+"+offset+")",build.isEmpty()?"":" [BuildId: "+build+"]");
    }
    private static String text(byte[] data){
        StringBuilder out=new StringBuilder("네이티브 상세 기록(text)\n");int kept=0;
        for(String line:new String(data,StandardCharsets.UTF_8).split("\\r?\\n")){
            if(line.matches(".*(Abort message:|signal [0-9]+|backtrace:|#[0-9]+\\s+pc|Cmdline:|pid:|ABI:|Cause:).*")){out.append(line.length()>2048?line.substring(0,2048):line).append('\n');if(++kept>=60)break;}
        }return out.toString();
    }
    private static final class Reader {
        final byte[] data;final int end;int pos;
        Reader(byte[] data,int start,int end){this.data=data;pos=start;this.end=end;}
        boolean more(){return pos<end;}
        long number()throws IOException {
            long value=0;
            for(int i=0;i<10;i++){if(pos>=end)throw new IOException("잘린 protobuf 값");int c=data[pos++]&255;if(i==9&&(c&254)!=0)throw new IOException("protobuf 값 범위 초과");value|=(long)(c&127)<<(i*7);if((c&128)==0)return value;}
            throw new IOException("protobuf 값 범위 초과");
        }
        int tag()throws IOException {long tag=number();if(tag<=0||tag>Integer.MAX_VALUE||(tag>>>3)==0)throw new IOException("잘못된 protobuf 필드");return (int)tag;}
        int length()throws IOException {long len=number();if(len<0||len>end-pos)throw new IOException("잘린 protobuf 데이터");return (int)len;}
        Reader block()throws IOException {int len=length();Reader r=new Reader(data,pos,pos+len);pos+=len;return r;}
        String string()throws IOException {int len=length();String value=new String(data,pos,Math.min(len,8192),StandardCharsets.UTF_8);pos+=len;return value;}
        void skip(int tag)throws IOException {
            int wire=tag&7;if(wire==0){number();return;}if(wire==2){int size=length();pos+=size;return;}int len=wire==1?8:wire==5?4:-1;
            if(len<0||len>end-pos)throw new IOException("지원되지 않거나 잘린 protobuf 필드");pos+=len;
        }
    }
}
