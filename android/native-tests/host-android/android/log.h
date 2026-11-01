// Minimal Linux test-only shim for production Android logging calls. No Android runtime emulation.
#pragma once
#include <cstdarg>
#include <cstdio>
enum {ANDROID_LOG_DEFAULT=1,ANDROID_LOG_VERBOSE=2,ANDROID_LOG_DEBUG=3,ANDROID_LOG_INFO=4,ANDROID_LOG_WARN=5,ANDROID_LOG_ERROR=6};
static inline int __android_log_is_loggable(int priority,const char *,int minimum){return priority>=minimum;}
static inline int __android_log_print(int,const char *tag,const char *format,...){
    std::fprintf(stderr,"[%s] ",tag);va_list args;va_start(args,format);int n=std::vfprintf(stderr,format,args);va_end(args);std::fputc('\n',stderr);return n;
}
static inline int __android_log_write(int,const char *tag,const char *message){return std::fprintf(stderr,"[%s] %s",tag,message?message:"");}
