#include <jni.h>
#include <string>
#include <cstdlib>
#include <vector>
#include <android/log.h>
#include "mdict-cpp/include/mdict_extern.h"
#include "mdict-cpp/include/mdict.h"

// Logging helper
#define LOG_TAG "MdictJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" {

// ----------------------------------------------------------------------------
// 1. Init from File Path
// ----------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_initDictionaryNative(
        JNIEnv* env,
        jobject /* this */,
        jstring filePath) {

    const char *path = env->GetStringUTFChars(filePath, 0);

    // Call mdict_init from mdict_extern.h
    void* dict_ptr = mdict_init(path);

    if (dict_ptr == nullptr) {
        LOGE("Failed to initialize dictionary at %s", path);
    }

    env->ReleaseStringUTFChars(filePath, path);
    return reinterpret_cast<jlong>(dict_ptr);
}

// ----------------------------------------------------------------------------
// 2. Init from File Descriptor (The one causing your crash)
// ----------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_initDictionaryFdNative(
        JNIEnv* env,
        jobject /* this */,
        jint fd,
        jboolean isMdd) {

    // Changed package name from 'waltermelon' to 'vibedict'
    void* dict_ptr = mdict_init_fd(fd, (bool)isMdd);

    if (dict_ptr == nullptr) {
        LOGE("Failed to initialize dictionary from file descriptor %d", fd);
    }

    return reinterpret_cast<jlong>(dict_ptr);
}

// ----------------------------------------------------------------------------
// 3. Lookup
// ----------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_lookupNative(
        JNIEnv* env,
        jobject /* this */,
        jlong dictHandle,
        jstring word) {

    if (dictHandle == 0) return nullptr;

    auto* dict = reinterpret_cast<mdict::Mdict*>(dictHandle);
    const char *c_word = env->GetStringUTFChars(word, 0);
    std::string s_word(c_word);
    env->ReleaseStringUTFChars(word, c_word);

    std::vector<std::string> results = dict->lookup(s_word);

    if (results.empty()) {
        return nullptr;
    }

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;

    jobjectArray stringArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    if (stringArray == nullptr) return nullptr;

    for (size_t i = 0; i < results.size(); ++i) {
        jstring javaString = env->NewStringUTF(results[i].c_str());
        env->SetObjectArrayElement(stringArray, i, javaString);
        env->DeleteLocalRef(javaString);
    }

    return stringArray;
}

// ----------------------------------------------------------------------------
// 4. Destroy
// ----------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_destroyNative(
        JNIEnv* env,
        jobject /* this */,
        jlong dictHandle) {

    if (dictHandle != 0) {
        void* dict = reinterpret_cast<void*>(dictHandle);
        mdict_destory(dict);
    }
}

// ----------------------------------------------------------------------------
// 5. Get Suggestions
// ----------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_getSuggestionsNative(
        JNIEnv* env,
        jobject /* this */,
        jlong dictHandle,
        jstring prefix) {

    if (dictHandle == 0) return nullptr;
    auto* dict = reinterpret_cast<mdict::Mdict*>(dictHandle);

    const char* c_prefix = env->GetStringUTFChars(prefix, 0);
    std::string s_prefix(c_prefix);
    env->ReleaseStringUTFChars(prefix, c_prefix);

    std::vector<std::string> suggestions = dict->suggest(s_prefix);

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;

    jobjectArray stringArray = env->NewObjectArray(suggestions.size(), stringClass, nullptr);
    if (stringArray == nullptr) return nullptr;

    for (size_t i = 0; i < suggestions.size(); ++i) {
        jstring javaString = env->NewStringUTF(suggestions[i].c_str());
        env->SetObjectArrayElement(stringArray, i, javaString);
        env->DeleteLocalRef(javaString);
    }

    return stringArray;
}

// ----------------------------------------------------------------------------
// 6. Get Match Count
// ----------------------------------------------------------------------------
JNIEXPORT jint JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_getMatchCountNative(
        JNIEnv *env,
        jobject thiz,
        jlong dictHandle,
        jstring jkey) {

    mdict::Mdict* md = reinterpret_cast<mdict::Mdict*>(dictHandle);
    if (!md) {
        return 0;
    }
    const char* key = env->GetStringUTFChars(jkey, 0);
    std::string key_str(key);
    int32_t count = md->get_match_count(key_str);
    env->ReleaseStringUTFChars(jkey, key);
    return count;
}

// ----------------------------------------------------------------------------
// 7. Get Regex Suggestions
// ----------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_getRegexSuggestionsNative(
        JNIEnv* env,
        jobject /* this */,
        jlong dictHandle,
        jstring regex) {

    if (dictHandle == 0) return nullptr;
    auto* dict = reinterpret_cast<mdict::Mdict*>(dictHandle);

    const char* c_regex = env->GetStringUTFChars(regex, 0);
    std::string s_regex(c_regex);
    env->ReleaseStringUTFChars(regex, c_regex);

    __android_log_print(ANDROID_LOG_DEBUG, "MdictJNI", "getRegexSuggestionsNative called with: %s", s_regex.c_str());

    std::vector<std::string> suggestions = dict->regex_suggest(s_regex);

    __android_log_print(ANDROID_LOG_DEBUG, "MdictJNI", "Found %zu suggestions", suggestions.size());

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;

    jobjectArray stringArray = env->NewObjectArray(suggestions.size(), stringClass, nullptr);
    if (stringArray == nullptr) return nullptr;

    for (size_t i = 0; i < suggestions.size(); ++i) {
        jstring javaString = env->NewStringUTF(suggestions[i].c_str());
        env->SetObjectArrayElement(stringArray, i, javaString);
        env->DeleteLocalRef(javaString);
    }

    return stringArray;
}

// ----------------------------------------------------------------------------
// 8. Get Full Text Suggestions
// ----------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_waltermelon_vibedict_data_MdictEngine_getFullTextSuggestionsNative(
        JNIEnv* env,
        jobject /* this */,
        jlong dictHandle,
        jstring query) {

    if (dictHandle == 0) return nullptr;
    auto* dict = reinterpret_cast<mdict::Mdict*>(dictHandle);

    const char* c_query = env->GetStringUTFChars(query, 0);
    std::string s_query(c_query);
    env->ReleaseStringUTFChars(query, c_query);

    __android_log_print(ANDROID_LOG_DEBUG, "MdictJNI", "getFullTextSuggestionsNative called with: %s", s_query.c_str());

    std::vector<std::string> suggestions = dict->fulltext_search(s_query);

    __android_log_print(ANDROID_LOG_DEBUG, "MdictJNI", "Found %zu full-text matches", suggestions.size());

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) return nullptr;

    jobjectArray stringArray = env->NewObjectArray(suggestions.size(), stringClass, nullptr);
    if (stringArray == nullptr) return nullptr;

    for (size_t i = 0; i < suggestions.size(); ++i) {
        jstring javaString = env->NewStringUTF(suggestions[i].c_str());
        env->SetObjectArrayElement(stringArray, i, javaString);
        env->DeleteLocalRef(javaString);
    }

    return stringArray;
}

} // extern "C"