package com.anisequel.data.extensions.runtime;

import android.os.ParcelFileDescriptor;
import com.anisequel.data.extensions.runtime.IRuntimeHost;

/**
 * The host's view of the sandboxed extension runtime (Phase 2.1).
 *
 * Four source calls - search, details, episodes, resolve - plus load/unload for
 * the lifecycle. Payloads are JSON strings (see RuntimeProtocol) so the surface
 * carries no custom parcelables and the protocol is unit-testable on the JVM.
 * The APK travels as a read-only file descriptor: an isolated process cannot
 * open the app's private files by path.
 */
interface IExtensionRuntime {
    String load(String requestJson, in ParcelFileDescriptor apk, IRuntimeHost host);
    String search(String sourceId, String query, int page);
    String details(String sourceId, String animeJson);
    String episodes(String sourceId, String animeJson);
    String resolve(String sourceId, String episodeJson);
    void unload(String packageName);
}