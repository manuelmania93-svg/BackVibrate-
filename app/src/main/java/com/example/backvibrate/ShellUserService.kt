package com.example.backvibrate

import android.content.Context
import kotlin.system.exitProcess

/** Runs inside the Shizuku user-service process (shell identity). Only used to run `pm grant`. */
class ShellUserService() : IUserService.Stub() {

    @Suppress("unused")
    constructor(context: Context) : this()

    override fun destroy() {
        exitProcess(0)
    }

    override fun exec(command: String): String {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            val code = p.waitFor()
            "exit=$code out=${out.trim()} err=${err.trim()}"
        } catch (e: Exception) {
            "exception=${e.message}"
        }
    }
}
