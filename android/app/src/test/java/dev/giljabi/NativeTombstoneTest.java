package dev.giljabi;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import static org.junit.Assert.*;
public class NativeTombstoneTest {
 private static byte[] join(byte[]...parts){ByteArrayOutputStream b=new ByteArrayOutputStream();for(byte[] p:parts)b.write(p,0,p.length);return b.toByteArray();}
 private static byte[] varint(long v){ByteArrayOutputStream b=new ByteArrayOutputStream();do{int c=(int)(v&127);v>>>=7;b.write(v==0?c:c|128);}while(v!=0);return b.toByteArray();}
 private static byte[] n(int field,long value){return join(varint(field*8),varint(value));}
 private static byte[] bytes(int field,byte[] value){return join(varint(field*8+2),varint(value.length),value);}
 private static byte[] s(int field,String value){return bytes(field,value.getBytes(StandardCharsets.UTF_8));}
 private static byte[] thread(int id,String name,String file){byte[] frame=join(n(1,0x1234),s(4,"fake_native_function"),n(5,16),s(6,file),s(8,"fake-build-id"));return bytes(16,join(n(1,id),bytes(2,join(n(1,id),s(2,name),bytes(4,frame),s(5,"DO_NOT_COPY_MEMORY")))));}
 private static byte[] sample(){return join(n(5,123),thread(42,"unrelated-thread","libwrong.so"),n(6,43),bytes(10,join(n(1,6),s(2,"SIGABRT"))),s(14,"FORTIFY: fake diagnostic assertion"),thread(43,"crashing-thread","libexample.so"),n(22,16384));}
 private static String read(byte[] raw)throws Exception{return NativeTombstone.read(new ByteArrayInputStream(raw));}
 @Test public void extractsAbortAndOnlyCrashingThreadRegardlessOfFieldOrder()throws Exception{String report=read(sample());assertTrue(report.contains("SIGABRT"));assertTrue(report.contains("FORTIFY: fake"));assertTrue(report.contains("crashing-thread"));assertTrue(report.contains("libexample.so"));assertTrue(report.contains("fake_native_function+16"));assertTrue(report.contains("16384"));assertFalse(report.contains("libwrong.so"));assertFalse(report.contains("DO_NOT_COPY_MEMORY"));}
 @Test public void skipsUnknownFieldsIncludingMemoryAndLogs()throws Exception{String report=read(join(s(17,"PRIVATE_MEMORY"),n(100,22),sample(),s(18,"PRIVATE_LOGS")));assertTrue(report.contains("libexample.so"));assertFalse(report.contains("PRIVATE"));}
 @Test public void reportsMissingAbortForOtherNativeSignals()throws Exception{String report=read(join(n(6,43),bytes(10,join(n(1,11),s(2,"SIGSEGV"))),thread(43,"crashing-thread","libexample.so")));assertTrue(report.contains("SIGSEGV"));assertTrue(report.contains("Abort message: 제공되지 않음"));}
 @Test public void toleratesMissingThreadWithoutGuessingAnotherOne()throws Exception{String report=read(join(n(6,99),thread(42,"other","libwrong.so")));assertTrue(report.contains("제공되지 않았"));assertFalse(report.contains("libwrong.so"));}
 @Test public void rejectsTruncatedAndOverflowedProto()throws Exception{assertThrows(IOException.class,()->read(new byte[]{114,10,1}));assertThrows(IOException.class,()->read(new byte[]{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,(byte)128,1}));assertThrows(IOException.class,()->read(new byte[]{0}));}
 @Test public void rejectsOversizedTraceBeforeParsing(){assertThrows(IOException.class,()->read(new byte[NativeTombstone.MAX_BYTES+1]));}
 @Test public void readsGzippedProtoWithinTheSameLimit()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(GZIPOutputStream gz=new GZIPOutputStream(b)){gz.write(sample());}assertTrue(read(b.toByteArray()).contains("libexample.so"));}
 @Test public void textFallbackDoesNotCopyMemoryDump()throws Exception{String raw="*** *** ***\nAbort message: fake abort\nsecret memory: not for report\nbacktrace:\n #00 pc 000123 libexample.so (abort+1)\n";String report=read(raw.getBytes(StandardCharsets.UTF_8));assertTrue(report.contains("fake abort"));assertTrue(report.contains("libexample.so"));assertFalse(report.contains("secret memory"));}
}
