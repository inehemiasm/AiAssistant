package com.neo.chevere.data.inference.minicpm

/** JNI entry points; callers serialize the handle and join native work before closing it. */
internal object MiniCpmNative {
    init { System.loadLibrary("chevere_minicpm") }
    external fun open(root: ByteArray): Long
    external fun begin(handle: Long, prompt: ByteArray, image: ByteArray?): Int
    external fun next(handle: Long): ByteArray?
    external fun arm(handle: Long)
    external fun cancel(handle: Long)
    external fun close(handle: Long)
}
