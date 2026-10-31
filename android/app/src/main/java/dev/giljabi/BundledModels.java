package dev.giljabi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Installs the two APK assets as one verified pair, without network access. */
final class BundledModels {
    interface Assets { InputStream open(String path) throws IOException; }
    interface Progress { void update(long copied, long total); }
    interface Cancellation { void check(); }
    final byte[] manifest;
    final long modelBytes, visionBytes;
    final String modelHash, visionHash, id;

    BundledModels(InputStream input) throws IOException {
        try(InputStream in=input; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int n;
            while((n=in.read(buffer))!=-1) {
                if(out.size()+n>16384)throw new IOException("내장 AI 정보가 너무 큽니다.");
                out.write(buffer,0,n);
            }
            manifest=out.toByteArray();
        }
        Properties p=new Properties();p.load(new ByteArrayInputStream(manifest));
        if(!"1".equals(p.getProperty("format")))throw new IOException("지원하지 않는 내장 AI 형식입니다.");
        modelBytes=size(p,"model.bytes");visionBytes=size(p,"vision.bytes");
        modelHash=hash(p,"model.sha256");visionHash=hash(p,"vision.sha256");
        id=hex(digest().digest(manifest));
    }
    private static long size(Properties p,String key) throws IOException {
        try { long n=Long.parseLong(p.getProperty(key,""));if(n<8||n>4L*1024*1024*1024)throw new NumberFormatException();return n; }
        catch(NumberFormatException e){throw new IOException("내장 AI 파일 크기가 올바르지 않습니다: "+key,e);}
    }
    private static String hash(Properties p,String key) throws IOException {
        String s=p.getProperty(key,"");if(!s.matches("[a-f0-9]{64}"))throw new IOException("내장 AI 해시가 올바르지 않습니다: "+key);return s;
    }
    File directory(File root){return new File(root,id);}
    boolean ready(File root) throws IOException {
        File dir=directory(root),marker=new File(dir,"complete");
        return new File(dir,"model.gguf").length()==modelBytes&&new File(dir,"mmproj.gguf").length()==visionBytes
            &&marker.isFile()&&Arrays.equals(Files.readAllBytes(marker.toPath()),manifest);
    }
    synchronized File install(Assets assets,File root,LongSupplier available,Progress progress,Cancellation cancellation) throws IOException {
        cancellation.check();if(ready(root))return directory(root);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("AI 저장 폴더를 만들지 못했습니다.");
        File destination=directory(root),staging=new File(root,id+".part");
        remove(staging);
        if(available.getAsLong()<modelBytes+visionBytes+64L*1024*1024)throw new IOException("내장 AI 준비 공간이 부족합니다. 약 "+((modelBytes+visionBytes)/1048576+64)+"MB의 여유 공간이 필요합니다.");
        if(!staging.mkdir())throw new IOException("AI 임시 폴더를 만들지 못했습니다.");
        try {
            copy(assets,"model.gguf",new File(staging,"model.gguf"),modelBytes,modelHash,0,progress,cancellation);
            copy(assets,"mmproj.gguf",new File(staging,"mmproj.gguf"),visionBytes,visionHash,modelBytes,progress,cancellation);
            cancellation.check();Files.write(new File(staging,"complete").toPath(),manifest);
            remove(destination);
            Files.move(staging.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
            return destination;
        } finally {remove(staging);}
    }
    private void copy(Assets assets,String name,File target,long expected,String expectedHash,long previous,Progress progress,Cancellation cancellation) throws IOException {
        MessageDigest sha=digest();long count=0,last=0;
        try(InputStream in=assets.open("local-ai/"+name);FileOutputStream out=new FileOutputStream(target)) {
            byte[] magic=new byte[4];new DataInputStream(in).readFully(magic);
            if(!Arrays.equals(magic,new byte[]{'G','G','U','F'}))throw new IOException("내장 AI가 GGUF 파일이 아닙니다: "+name);
            out.write(magic);sha.update(magic);count=4;
            byte[] buffer=new byte[1024*1024];int n;
            while((n=in.read(buffer))!=-1) {
                cancellation.check();count+=n;if(count>expected)throw new IOException("내장 AI 크기가 다릅니다: "+name);
                out.write(buffer,0,n);sha.update(buffer,0,n);
                if(count-last>=32L*1024*1024){progress.update(previous+count,modelBytes+visionBytes);last=count;}
            }
            if(count!=expected||!hex(sha.digest()).equals(expectedHash))throw new IOException("내장 AI 파일 검증 실패: "+name);
            out.getFD().sync();progress.update(previous+count,modelBytes+visionBytes);
        }
    }
    private static void remove(File file) throws IOException {
        if(!file.exists())return;
        File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);
        if(!file.delete())throw new IOException("AI 임시 파일을 정리하지 못했습니다.");
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
