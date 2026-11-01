package dev.giljabi;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;
public class CameraFailureTest {
 @Test public void networkTimeoutCanReconnect(){assertTrue(CameraFailure.from(new java.net.SocketTimeoutException("read timed out")).retryable);}
 @Test public void memoryFailureStopsRetryLoop(){CameraFailure failure=CameraFailure.from(new OutOfMemoryError("alloc"));assertFalse(failure.retryable);assertTrue(failure.message.contains("메모리"));}
 @Test public void nestedNativeLoaderFailureIsReported(){CameraFailure failure=CameraFailure.from(new Exception(new UnsatisfiedLinkError("ABI")));assertFalse(failure.retryable);assertTrue(failure.message.contains("라이브러리"));}
 @Test public void wrappedOutOfMemoryIsNotRetriedAsIo(){assertFalse(CameraFailure.from(new IOException("wrapped",new OutOfMemoryError())).retryable);}
 @Test public void invalidModelOrUiFailureIsNotReconnectedForever(){assertFalse(CameraFailure.from(new IllegalArgumentException("Unsupported YOLO output")).retryable);}
}
