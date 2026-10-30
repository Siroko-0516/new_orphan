package dev.giljabi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BundledModelsTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    private static final byte[] MODEL="GGUFmodel-data".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] VISION="GGUFvision-data".getBytes(StandardCharsets.US_ASCII);
    private static String sha(byte[] b)throws Exception{return BundledModels.hex(MessageDigest.getInstance("SHA-256").digest(b));}
    private BundledModels spec()throws Exception {
        String metadata="format=1\nmodel.bytes="+MODEL.length+"\nvision.bytes="+VISION.length+"\nmodel.sha256="+sha(MODEL)+"\nvision.sha256="+sha(VISION)+"\n";
        return new BundledModels(new ByteArrayInputStream(metadata.getBytes(StandardCharsets.US_ASCII)));
    }
    private BundledModels.Assets assets(byte[] vision){return path->new ByteArrayInputStream(path.endsWith("/model.gguf")?MODEL:vision);}
    private File install(BundledModels s,File root,BundledModels.Assets assets)throws IOException {
        return s.install(assets,root,Long.MAX_VALUE,(n,total)->{},()->{});
    }
    @Test public void installsVerifiedPairAndReusesItWithoutOpeningAssets()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();File dir=install(s,root,assets(VISION));
        assertArrayEquals(MODEL,Files.readAllBytes(new File(dir,"model.gguf").toPath()));
        assertArrayEquals(VISION,Files.readAllBytes(new File(dir,"mmproj.gguf").toPath()));
        assertTrue(s.ready(root));
        assertEquals(dir,s.install(p->{throw new AssertionError("Already installed");},root,0,(n,t)->{},()->{}));
    }
    @Test public void checksumFailurePublishesNeitherFile()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();byte[] wrong=VISION.clone();wrong[5]^=1;
        assertThrows(IOException.class,()->install(s,root,assets(wrong)));
        assertFalse(s.directory(root).exists());assertEquals(0,Objects.requireNonNull(root.list()).length);
    }
    @Test public void truncatedVisionDoesNotLeavePartialPair()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();
        assertThrows(IOException.class,()->install(s,root,assets(Arrays.copyOf(VISION,8))));
        assertFalse(s.ready(root));assertEquals(0,Objects.requireNonNull(root.list()).length);
    }
    @Test public void cancellationDuringSecondFileCleansStaging()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();boolean[] stop={false};
        assertThrows(CancellationException.class,()->s.install(assets(VISION),root,Long.MAX_VALUE,(n,t)->stop[0]=true,()->{if(stop[0])throw new CancellationException();}));
        assertFalse(s.ready(root));assertEquals(0,Objects.requireNonNull(root.list()).length);
    }
    @Test public void insufficientSpaceDoesNotOpenLargeAssets()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();
        assertThrows(IOException.class,()->s.install(p->{throw new AssertionError("No space");},root,0,(n,t)->{},()->{}));
        assertEquals(0,Objects.requireNonNull(root.list()).length);
    }
    @Test public void failedUpdatePreservesPreviousVersion()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();File previous=new File(root,"previous-version");assertTrue(previous.mkdir());
        File marker=new File(previous,"model.gguf");Files.write(marker.toPath(),MODEL);
        assertThrows(IOException.class,()->install(s,root,assets(new byte[8])));
        assertArrayEquals(MODEL,Files.readAllBytes(marker.toPath()));
    }
    @Test public void corruptedCacheLengthIsReinstalled()throws Exception {
        BundledModels s=spec();File root=temporary.newFolder();File dir=install(s,root,assets(VISION));
        Files.write(new File(dir,"mmproj.gguf").toPath(),new byte[0]);assertFalse(s.ready(root));
        install(s,root,assets(VISION));assertTrue(s.ready(root));
    }
}
