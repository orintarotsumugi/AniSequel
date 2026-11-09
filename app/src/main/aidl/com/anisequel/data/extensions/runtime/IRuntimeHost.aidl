package com.anisequel.data.extensions.runtime;

/**
 * The runtime's only channel back to the host: proxied HTTP.
 *
 * An isolated process holds no permissions, so it has no INTERNET permission of
 * its own. Extension network calls are marshalled here and performed by the
 * host's client, which keeps the app's tokens and the extension's traffic apart.
 * The payload is JSON (url, method, headers, base64 body) - see RuntimeProtocol.
 */
interface IRuntimeHost {
    String fetch(String requestJson);
}