package org.hermesmobile.app.ui

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Examples are static instructions; saved addresses never become shell commands. */
internal object SetupGuideContent {
    data class Page(val heading: Words,val body: Words,val docs: Words,val url: String)
    private const val apiDocs="https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server"
    private const val dashboardDocs="https://hermes-agent.nousresearch.com/docs/user-guide/features/web-dashboard"
    val pages=listOf(
        Page(Words.SetupHost,Words.SetupHostBody,Words.SetupInstallDocs,"https://hermes-agent.nousresearch.com/docs/getting-started/installation"),
        Page(Words.SetupApi,Words.SetupApiBody,Words.SetupApiDocs,apiDocs),
        Page(Words.SetupNetwork,Words.SetupNetworkBody,Words.SetupTailscaleDocs,"https://tailscale.com/docs/features/tailscale-serve"),
        Page(Words.SetupPhone,Words.SetupPhoneBody,Words.SetupApiDocs,apiDocs),
        Page(Words.SetupDashboard,Words.SetupDashboardBody,Words.SetupDashboardDocs,dashboardDocs),
        Page(Words.SetupModels,Words.SetupModelsBody,Words.SetupApiDocs,apiDocs),
        Page(Words.SetupChats,Words.SetupChatsBody,Words.SetupApiDocs,apiDocs),
        Page(Words.SetupProjects,Words.SetupProjectsBody,Words.SetupDashboardDocs,dashboardDocs),
        Page(Words.SetupVerify,Words.SetupVerifyBody,Words.SetupApiDocs,apiDocs))

    const val dashboardConfig="HERMES_DASHBOARD_BASIC_AUTH_USERNAME=<your-login-name>\nHERMES_DASHBOARD_BASIC_AUTH_PASSWORD=<your-new-private-password>\nHERMES_DASHBOARD_BASIC_AUTH_SECRET=<your-stable-random-signing-secret>\nHERMES_DASHBOARD_PUBLIC_URL=https://your-host.your-tailnet.ts.net:8443"
    const val dashboardStart="hermes dashboard --host 127.0.0.1 --port 9119 --no-open"

    fun savedApi(value: String): String? {
        if(value.length>2048 || value.any {it.isWhitespace() || Character.isISOControl(it) || Character.getType(it)==Character.FORMAT.toInt()}) return null
        val uri=runCatching {URI(value)}.getOrNull() ?: return null
        if(uri.rawUserInfo!=null || uri.rawQuery!=null || uri.rawFragment!=null) return null
        val url=value.toHttpUrlOrNull() ?: return null
        return url.takeIf {it.isHttps && it.username.isEmpty() && it.password.isEmpty()}?.toString()
    }

    fun example(step: Int,newHost: Boolean,savedUrl: String): String? = when(step) {
        0 -> if(newHost) "curl -fsSL https://hermes-agent.nousresearch.com/install.sh | bash\nhermes setup\nhermes" else "hermes --version\nhermes doctor"
        1 -> "API_SERVER_ENABLED=true\nAPI_SERVER_HOST=127.0.0.1\nAPI_SERVER_PORT=8642\nAPI_SERVER_KEY=<your-private-strong-key>"
        2 -> if(newHost) "tailscale serve status\nsudo tailscale serve --bg --https=443 http://127.0.0.1:8642\ntailscale serve status" else "tailscale status\ntailscale serve status"
        3 -> savedApi(savedUrl) ?: "https://your-host.your-tailnet.ts.net/"
        4 -> if(newHost) "sudo tailscale serve --bg --https=8443 http://127.0.0.1:9119" else "tailscale serve status"
        8 -> "Reply only: Signal Grove connection OK. Do not use tools."
        else -> null
    }
}
