class NativeGuardProbe {
 static {System.loadLibrary("giljabi_guard_probe");}
 static native int probe(int kind);
 public static void main(String[] args){
  for(int i=0;i<3;i++){boolean caught=false;try{probe(i);}catch(IllegalStateException e){if(i==1)throw e;caught=true;}catch(OutOfMemoryError e){if(i!=1)throw e;caught=true;}if(!caught)throw new AssertionError("Native exception escaped guard");}
  if(probe(3)!=42)throw new AssertionError("Success path changed");System.out.println("JNI_GUARD_PASS");
 }
}
