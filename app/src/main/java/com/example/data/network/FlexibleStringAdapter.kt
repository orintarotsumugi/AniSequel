package com.example.data.network

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter

/**
 * Normalizes JSON values that may appear as numbers (e.g. 7), strings (e.g. "7", "1 - 12"),
 * or null into a [String].
 *
 * ## Why this exists for AniList activity
 *
 * AniList GraphQL schema defines `ListActivity.progress` as a `String` (describing
 * either a single episode number or a multi-chapter range like "1 - 10"). However,
 * unit fixtures, test mocks, or alternative feeds may serialize numerical progress
 * as a JSON number. Moshi's default string adapter throws a `JsonDataException` when
 * encountering a number token, and its int adapter throws when encountering a string.
 * This adapter gracefully accepts both token types without failing the entire query.
 */
object FlexibleStringAdapter : JsonAdapter<String>() {

    override fun fromJson(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonReader.Token.NULL -> reader.nextNull()
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextString()
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    override fun toJson(writer: JsonWriter, value: String?) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
