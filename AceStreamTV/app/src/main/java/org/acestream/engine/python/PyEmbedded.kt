package org.acestream.engine.python

/**
 * JNI Wrapper for AceStream PyEmbedded native library
 */
class PyEmbedded {
    companion object {
        /**
         * Get the compiled ABI string
         */
        @JvmStatic
        external fun getCompiledABI(): String

        /**
         * Run a Python script.
         *
         * IMPORTANT:
         * libpyembedded.so exports Java_org_acestream_engine_python_PyEmbedded_runScript
         * which corresponds to a *static* native method.
         *
         * Second parameter must be a single String (native calls String.getBytes()).
         */
        @JvmStatic
        external fun runScript(context: android.content.Context, cmdline: String)

        /**
         * Wait for a process ID (optional)
         */
        @JvmStatic
        external fun waitPid(pid: Int)
    }
}

