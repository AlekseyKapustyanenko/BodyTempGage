package com.bodytempgage.common.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * File-backed persistence for the temperature history, so the chart survives process
 * restarts. Samples are appended as JSON lines to a file in [Context.getFilesDir];
 * the file is compacted (rewritten with only the retained window) on load when it has
 * accumulated enough stale lines, and periodically during long monitoring runs.
 *
 * All methods do file I/O — call them off the main thread. Methods are synchronized,
 * so concurrent appends from a dispatcher pool cannot interleave lines.
 */
class HistoryStore(context: Context) {

    private val file = File(context.filesDir, "temp-history.jsonl")

    private var appendsSinceCompact = 0

    /** Read all samples newer than [nowMillis] − [windowMillis], oldest first. */
    @Synchronized
    fun load(nowMillis: Long, windowMillis: Long): List<TempSample> {
        if (!file.exists()) return emptyList()
        val cutoff = nowMillis - windowMillis
        var totalLines = 0
        val kept = runCatching {
            file.useLines { lines ->
                lines.mapNotNull { line ->
                    totalLines++
                    decode(line)?.takeIf { it.timestampMillis >= cutoff }
                }.toList()
            }
        }.getOrDefault(emptyList()).sortedBy { it.timestampMillis }
        // Shed stale lines so the file doesn't grow across many short sessions.
        if (totalLines - kept.size > COMPACT_SLACK_LINES) rewrite(kept)
        return kept
    }

    /**
     * Persist one new sample. [retained] is the full in-memory history including the
     * sample; every [COMPACT_EVERY_APPENDS] appends the file is rewritten from it so
     * an uninterrupted monitoring run can't grow the file without bound.
     */
    @Synchronized
    fun append(sample: TempSample, retained: List<TempSample>) {
        runCatching {
            if (appendsSinceCompact >= COMPACT_EVERY_APPENDS) {
                rewrite(retained)
            } else {
                file.appendText(encode(sample) + "\n")
                appendsSinceCompact++
            }
        }
    }

    /** Drop the persisted history (e.g. after switching to another device). */
    @Synchronized
    fun clear() {
        file.delete()
        appendsSinceCompact = 0
    }

    private fun rewrite(samples: List<TempSample>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(samples.joinToString(separator = "\n", postfix = "\n") { encode(it) })
        tmp.renameTo(file)
        appendsSinceCompact = 0
    }

    private fun encode(sample: TempSample): String = JSONObject().apply {
        put(KEY_TIME, sample.timestampMillis)
        sample.bodyTempC?.let { put(KEY_BODY, it) }
        sample.gaugeTempC?.let { put(KEY_GAUGE, it) }
    }.toString()

    private fun decode(line: String): TempSample? = runCatching {
        val json = JSONObject(line)
        TempSample(
            timestampMillis = json.getLong(KEY_TIME),
            bodyTempC = if (json.has(KEY_BODY)) json.getDouble(KEY_BODY) else null,
            gaugeTempC = if (json.has(KEY_GAUGE)) json.getDouble(KEY_GAUGE) else null,
        )
    }.getOrNull()

    private companion object {
        const val KEY_TIME = "t"
        const val KEY_BODY = "b"
        const val KEY_GAUGE = "g"
        const val COMPACT_EVERY_APPENDS = 5_000
        const val COMPACT_SLACK_LINES = 500
    }
}
