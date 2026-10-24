#include <jni.h>
#include <string>
#include "whisper.h"
extern "C" JNIEXPORT jstring JNICALL
Java_com_lyricmotion_app_WhisperEngine_transcribe(JNIEnv* env,jclass, jstring modelPath,jfloatArray samples,jstring language){
    const char* path=env->GetStringUTFChars(modelPath,nullptr);
    const char* lang=env->GetStringUTFChars(language,nullptr);
    whisper_context_params ctxp=whisper_context_default_params();
    whisper_context* ctx=whisper_init_from_file_with_params(path,ctxp);
    env->ReleaseStringUTFChars(modelPath,path);
    if(!ctx){env->ReleaseStringUTFChars(language,lang);return env->NewStringUTF("{\"error\":\"Model load failed\"}");}
    jsize n=env->GetArrayLength(samples);
    jfloat* pcm=env->GetFloatArrayElements(samples,nullptr);
    whisper_full_params p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.language=lang; p.translate=false;p.no_context=true;p.print_progress=false;p.print_realtime=false;p.print_timestamps=false;p.print_special=false;
    int status=whisper_full(ctx,p,pcm,n);
    env->ReleaseFloatArrayElements(samples,pcm,JNI_ABORT);
    env->ReleaseStringUTFChars(language,lang);
    std::string json="[";
    if(status==0){
        int count=whisper_full_n_segments(ctx);
        for(int i=0;i<count;i++){
            if(i)json+=",";
            std::string txt=whisper_full_get_segment_text(ctx,i);
            std::string escaped;
            for(char c:txt){if(c=='"'||c=='\\')escaped+='\\';if(c=='\n'||c=='\r')c=' ';escaped+=c;}
            json+="{\"start\":"+std::to_string(whisper_full_get_segment_t0(ctx,i)/100.0)+",\"end\":"+std::to_string(whisper_full_get_segment_t1(ctx,i)/100.0)+",\"text\":\""+escaped+"\"}";
        }
    }
    json+="]";
    whisper_free(ctx);
    if(status!=0)return env->NewStringUTF("{\"error\":\"Whisper inference failed\"}");
    return env->NewStringUTF(json.c_str());
}
