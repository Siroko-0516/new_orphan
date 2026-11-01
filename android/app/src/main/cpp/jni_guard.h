// SPDX-License-Identifier: MIT
#pragma once
#include <jni.h>
#include <cstdio>
#include <exception>
#include <new>
#include <stdexcept>
#include <type_traits>

namespace giljabi {
// No C++ exception may cross the VM's JNI boundary. Keep the error path allocation-free.
inline void native_error(JNIEnv *env,const char *where,const char *detail,const char *type) noexcept {
    if(!env||env->ExceptionCheck())return;
    char message[1024];std::snprintf(message,sizeof(message),"%s: %s",where,detail?detail:"native failure");
    // ThrowNew expects modified UTF-8; exception diagnostics need only safe ASCII here.
    for(char *p=message;*p;++p)if(static_cast<unsigned char>(*p)<32||static_cast<unsigned char>(*p)>126)*p='?';
    jclass error=env->FindClass(type);if(error){env->ThrowNew(error,message);env->DeleteLocalRef(error);}
}
template<typename R,typename F>
R jni_guard(JNIEnv *env,const char *where,F &&operation) noexcept {
    try{return operation();}
    catch(const std::bad_alloc &){native_error(env,where,"Native allocation failed","java/lang/OutOfMemoryError");}
    catch(const std::exception &error){native_error(env,where,error.what(),"java/lang/IllegalStateException");}
    catch(...){native_error(env,where,"Unknown native exception","java/lang/IllegalStateException");}
    if constexpr(std::is_void_v<R>)return;
    else if constexpr(std::is_pointer_v<R>)return nullptr;
    else return static_cast<R>(-1);
}
class utf_chars {
    JNIEnv *env_;jstring value_;const char *chars_;
public:
    utf_chars(JNIEnv *env,jstring value):env_(env),value_(value),chars_(nullptr){
        if(!value)throw std::invalid_argument("Null text argument");
        chars_=env_->GetStringUTFChars(value_,nullptr);if(!chars_)throw std::bad_alloc();
    }
    utf_chars(const utf_chars &)=delete;utf_chars &operator=(const utf_chars &)=delete;
    ~utf_chars(){release();}
    const char *get()const{return chars_;}
    void release(){if(chars_){env_->ReleaseStringUTFChars(value_,chars_);chars_=nullptr;}}
};
class byte_elements {
    JNIEnv *env_;jbyteArray value_;jbyte *bytes_;
public:
    byte_elements(JNIEnv *env,jbyteArray value):env_(env),value_(value),bytes_(nullptr){
        if(!value)throw std::invalid_argument("Null image argument");
        bytes_=env_->GetByteArrayElements(value_,nullptr);if(!bytes_)throw std::bad_alloc();
    }
    byte_elements(const byte_elements &)=delete;byte_elements &operator=(const byte_elements &)=delete;
    ~byte_elements(){release();}
    jbyte *get()const{return bytes_;}
    void release(){if(bytes_){env_->ReleaseByteArrayElements(value_,bytes_,JNI_ABORT);bytes_=nullptr;}}
};
}
