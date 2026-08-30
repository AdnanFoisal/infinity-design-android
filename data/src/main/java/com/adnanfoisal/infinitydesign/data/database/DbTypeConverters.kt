package com.adnanfoisal.infinitydesign.data.database

import androidx.room.TypeConverter

class DbTypeConverters {
    @TypeConverter
    fun byteArrayToString(bytes: ByteArray?): String? = bytes?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }

    @TypeConverter
    fun stringToByteArray(s: String?): ByteArray? = s?.let { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }
}
