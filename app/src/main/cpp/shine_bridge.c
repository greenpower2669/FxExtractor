/*
 * JNI bridge to unmodified Shine encoder sources (LGPL 2.0).
 * Original library: https://github.com/toots/shine
 * See THIRD_PARTY_NOTICES.md for attribution and complete source information.
 */
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "layer3.h"

static void throw_io(JNIEnv *env, const char *message) {
    jclass type = (*env)->FindClass(env, "java/io/IOException");
    if (type) (*env)->ThrowNew(env, type, message);
}

JNIEXPORT jlong JNICALL
Java_fr_fxextractor_Mp3ShineEncoder_nativeOpen(
        JNIEnv *env, jclass cls, jint channels, jint sample_rate, jint bitrate) {
    (void)cls;
    if ((channels != 1 && channels != 2) ||
            sample_rate != 44100 || (bitrate != 128 && bitrate != 192) ||
            shine_check_config(sample_rate, bitrate) < 0) {
        throw_io(env, "Configuration MP3 invalide");
        return 0;
    }
    shine_config_t cfg;
    memset(&cfg, 0, sizeof(cfg));
    shine_set_config_mpeg_defaults(&cfg.mpeg);
    cfg.wave.channels = channels == 1 ? PCM_MONO : PCM_STEREO;
    cfg.wave.samplerate = sample_rate;
    cfg.mpeg.mode = channels == 1 ? MONO : STEREO;
    cfg.mpeg.bitr = bitrate;
    shine_t encoder = shine_initialise(&cfg);
    if (!encoder) {
        throw_io(env, "Mémoire native MP3 insuffisante");
        return 0;
    }
    return (jlong)(uintptr_t)encoder;
}

JNIEXPORT jint JNICALL
Java_fr_fxextractor_Mp3ShineEncoder_nativeSamplesPerPass(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    if (!handle) {
        throw_io(env, "Encodeur MP3 non initialisé");
        return 0;
    }
    return shine_samples_per_pass((shine_t)(uintptr_t)handle);
}

JNIEXPORT jbyteArray JNICALL
Java_fr_fxextractor_Mp3ShineEncoder_nativeEncode(
        JNIEnv *env, jclass cls, jlong handle, jshortArray pcm) {
    (void)cls;
    if (!handle || !pcm) {
        throw_io(env, "Tampon MP3 invalide");
        return NULL;
    }
    shine_t encoder = (shine_t)(uintptr_t)handle;
    const int samples = shine_samples_per_pass(encoder);
    /* Matches count from Java; length >= samples/channel is not sufficient. */
    jsize length = (*env)->GetArrayLength(env, pcm);
    if (length != samples && length != samples * 2) {
        throw_io(env, "Taille de trame MP3 incorrecte");
        return NULL;
    }
    jshort *samples_buffer = (*env)->GetShortArrayElements(env, pcm, NULL);
    if (!samples_buffer) return NULL;
    int written = 0;
    unsigned char *encoded =
            shine_encode_buffer_interleaved(encoder, (int16_t *)samples_buffer,
                                            &written);
    (*env)->ReleaseShortArrayElements(env, pcm, samples_buffer, JNI_ABORT);
    if (written < 0 || written > 32768 || (written > 0 && !encoded)) {
        throw_io(env, "Erreur interne de l'encodeur MP3");
        return NULL;
    }
    jbyteArray out = (*env)->NewByteArray(env, written);
    if (out && written)
        (*env)->SetByteArrayRegion(env, out, 0, written, (const jbyte *)encoded);
    return out;
}

JNIEXPORT jbyteArray JNICALL
Java_fr_fxextractor_Mp3ShineEncoder_nativeFlush(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)cls;
    if (!handle) {
        throw_io(env, "Encodeur MP3 non initialisé");
        return NULL;
    }
    int written = 0;
    unsigned char *data = shine_flush((shine_t)(uintptr_t)handle, &written);
    if (written < 0 || written > 32768 || (written > 0 && !data)) {
        throw_io(env, "Fin du flux MP3 invalide");
        return NULL;
    }
    jbyteArray out = (*env)->NewByteArray(env, written);
    if (out && written)
        (*env)->SetByteArrayRegion(env, out, 0, written, (const jbyte *)data);
    return out;
}

JNIEXPORT void JNICALL
Java_fr_fxextractor_Mp3ShineEncoder_nativeClose(
        JNIEnv *env, jclass cls, jlong handle) {
    (void)env;
    (void)cls;
    if (handle)
        shine_close((shine_t)(uintptr_t)handle);
}
