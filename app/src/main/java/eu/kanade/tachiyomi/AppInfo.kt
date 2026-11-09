package eu.kanade.tachiyomi

/**
 * Clean-room Aniyomi host-info ABI. Extensions read this for User-Agent strings;
 * it reports AniSequel's own version, which is the honest answer.
 */
@Suppress("unused")
object AppInfo {
    fun getVersionCode(): Int = com.anisequel.BuildConfig.VERSION_CODE
    fun getVersionName(): String = com.anisequel.BuildConfig.VERSION_NAME
}