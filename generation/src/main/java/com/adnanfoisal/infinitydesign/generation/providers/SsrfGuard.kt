package com.adnanfoisal.infinitydesign.generation.providers

import java.net.InetAddress
import java.net.URI

/**
 * SSRF protection. Section 40 of the spec.
 *
 * LiteLLM URLs are user-supplied. Without validation, a malicious URL could
 * make the backend fetch internal metadata endpoints (169.254.169.254 etc).
 *
 * This object either:
 *  - allows a URL pointing to a public IP/hostname, or
 *  - rejects it with a structured error.
 *
 * A small allowlist can be configured for local-only setups (e.g. http://localhost:4000
 * for a self-hosted LiteLLM proxy on the user's own machine).
 */
object SsrfGuard {

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]")
    private val LINK_LOCAL_PREFIXES = listOf(
        "169.254.",   // link-local
        "10.",
        "172.16.", "172.17.", "172.18.", "172.19.", "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.", "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
        "192.168.",
        "fc00::", "fd00::", "fe80::",
    )

    private val allowLocalOverride: Boolean = System.getProperty("infinitydesign.litellm.allow.local") == "true"

    fun validate(rawUrl: String): String? {
        val parsed = try { URI(rawUrl) } catch (_: Throwable) { return null }
        if (parsed.scheme !in setOf("http", "https")) return null
        val host = parsed.host ?: return null
        if (host.isBlank()) return null

        // Allow local override only when explicitly enabled AND the host is local.
        val isLocal = LOCAL_HOSTS.contains(host) || LINK_LOCAL_PREFIXES.any { host.startsWith(it) }
        if (isLocal) return if (allowLocalOverride) rawUrl else null

        // For hostnames, resolve and check the resolved address — guard against
        // DNS rebinding by ensuring the resolved IP is not private.
        return try {
            val addrs = InetAddress.getAllByName(host)
            val anyPrivate = addrs.any { addr ->
                val ip = addr.hostAddress ?: ""
                LINK_LOCAL_PREFIXES.any { ip.startsWith(it) } || LOCAL_HOSTS.contains(ip)
            }
            if (anyPrivate && !allowLocalOverride) null else rawUrl
        } catch (_: Throwable) {
            // Unresolvable host — reject.
            null
        }
    }
}
