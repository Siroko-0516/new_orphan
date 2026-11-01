#include "jni_guard.h"
extern "C" JNIEXPORT jint JNICALL Java_NativeGuardProbe_probe(JNIEnv *env,jclass,jint kind){
 return giljabi::jni_guard<jint>(env,__func__,[&]()->jint{if(kind==0)throw std::invalid_argument("fixture invalid argument");if(kind==1)throw std::bad_alloc();if(kind==2)throw 7;return 42;});
}
