package org.hermesmobile.app.local

import java.net.URI
import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.*
import org.hermesmobile.app.ui.SetupGuideContent
import org.hermesmobile.app.ui.Words

enum class LocalHelpTopic(val title: Words, val page: Int?, internal val searchTerms: List<String>) {
    FIRST_SETUP(Words.SetupPhone, 3, listOf("setup", "set up", "onboarding", "first time", "配置手机", "首次设置")),
    NETWORK(Words.SetupNetwork, 2, listOf("network", "connect", "connection", "timeout", "tailscale", "tailnet", "tls", "网络", "连接")),
    AUTH(Words.SetupApi, 1, listOf("login", "sign in", "signed in", "authentication", "api key", "401", "403", "登录", "认证", "密钥")),
    MODEL(Words.SetupModels, 5, listOf("spark", "serving model", "session lock", "server default", "model selection", "服务模型", "会话锁")),
    HISTORY(Words.SetupChats, 6, listOf("history", "conversation", "conversations", "earlier chats", "missing chats", "历史", "会话")),
    PROJECT(Words.SetupProjects, 7, listOf("project", "projects", "workspace", "worker", "项目", "工作区", "执行主机")),
    OFFLINE_MODEL(Words.PhoneOfflineSetup, null, listOf("offline", "on device", "gguf", "model", "models", "local model", "phone model",
        "storage", "space", "memory", "ram", "context", "load", "import", "lfm3", "离线", "本机模型", "手机模型", "空间", "存储",
        "内存", "上下文", "加载", "导入", "li xian", "lixian", "nei cun", "neicun", "shang xia wen", "shangxiawen"))
}
enum class LocalAddressKind { UNCONFIGURED, HTTPS, OTHER }

/** Deliberately no String fields for hostnames, paths, profile names, credentials or logs. */
data class LocalHelpObservations(val capturedAt: Long, val demo: Boolean, val address: LocalAddressKind,
    val process64Bit: Boolean, val modelLoaded: Boolean) {
    companion object {
        fun capture(demo: Boolean, savedUrl: String, process64Bit: Boolean, modelLoaded: Boolean, now: Long): LocalHelpObservations {
            val address = if(savedUrl.isBlank()) LocalAddressKind.UNCONFIGURED else
                if(runCatching { URI(savedUrl).scheme.equals("https", true) }.getOrDefault(false)) LocalAddressKind.HTTPS else LocalAddressKind.OTHER
            return LocalHelpObservations(now, demo, address, process64Bit, modelLoaded)
        }
    }
}

enum class LocalHelpScope { HERMES_HOST, PHONE_APP, EXECUTION_WORKER, REFERENCE }
data class LocalHelpSource(val id: String, val heading: Words, val body: Words, val url: String, val example: String? = null,
    val scope: LocalHelpScope = LocalHelpScope.REFERENCE)
data class LocalHelpCheck(val id: String, val word: Words, val sourceId: String, val scope: LocalHelpScope)

data class LocalHelpResult(val topic: LocalHelpTopic, val modelSha256: String, val observations: LocalHelpObservations?, val sources: List<LocalHelpSource>,
    val checks: List<LocalHelpCheck> = emptyList(), val state: LocalReplyState = LocalReplyState.GENERATING)

/** The model ranks approved checks. It never authors the instructions displayed to a novice. */
object LocalSetupHelper {
    const val VERSION = "signal-grove-setup-checks-2026-11-20"
    const val MAX_QUERY_LENGTH = 240
    private val combiningMarks = Regex("\\p{M}+")
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    private fun normalize(query: String) = Normalizer.normalize(query.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(combiningMarks, "").replace(separators, " ").trim()

    /** Local topic lookup. Only the explicitly selected enum is used in a model request. */
    fun matchingTopics(query: String): List<LocalHelpTopic> {
        val text = normalize(query.take(MAX_QUERY_LENGTH))
        if (text.isBlank()) return emptyList()
        val padded = " $text "
        return LocalHelpTopic.entries.filter { topic ->
            topic.searchTerms.any { term ->
                if (term.any { it in '\u4e00'..'\u9fff' }) text.contains(term)
                else padded.contains(" $term ")
            }
        }
    }

    fun sources(topic: LocalHelpTopic): List<LocalHelpSource> {
        val pageIndex = topic.page ?: return listOf(
            LocalHelpSource("S1", Words.PhoneOfflineSetup, Words.PhoneOfflineSetupGetHelp,
                "https://github.com/DionCroft/Signal_Grove/blob/main/docs/ON_DEVICE_AI.md#setup-and-troubleshooting",
                scope = LocalHelpScope.PHONE_APP)
        )
        val page = SetupGuideContent.pages[pageIndex]
        val scope = when(topic) {LocalHelpTopic.AUTH, LocalHelpTopic.NETWORK -> LocalHelpScope.HERMES_HOST; else -> LocalHelpScope.PHONE_APP}
        return listOf(LocalHelpSource("S1", page.heading, page.body, page.url, SetupGuideContent.example(pageIndex, false, ""), scope),
            LocalHelpSource("S2", Words.SetupTroubleshoot, Words.SetupTroubleshootBody,
                "https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server"))
    }
    fun checks(topic: LocalHelpTopic, observations: LocalHelpObservations? = null): List<LocalHelpCheck> {
        fun phone(id: String, word: Words, source: String = "S1") = LocalHelpCheck(id, word, source, LocalHelpScope.PHONE_APP)
        fun host(id: String, word: Words) = LocalHelpCheck(id, word, "S1", LocalHelpScope.HERMES_HOST)
        return when(topic) {
            LocalHelpTopic.FIRST_SETUP -> listOf(phone("phone.demo", Words.HelpDemo), phone("phone.tailnet", Words.HelpTailnet),
                phone("phone.url", Words.HelpUrl), phone("phone.api_key", Words.HelpKey), phone("phone.test", Words.HelpConnect))
                .filterNot {it.id == "phone.demo" && observations?.demo == false}
            LocalHelpTopic.NETWORK -> listOf(phone("phone.tailnet", Words.HelpTailnet), host("host.tailscale", Words.HelpHostStatus),
                host("host.serve", Words.HelpHostServe), host("host.private", Words.HelpPrivate), phone("phone.tls", Words.HelpTls, "S2"))
            LocalHelpTopic.AUTH -> listOf(host("host.api", Words.HelpHostApi), phone("phone.api_key", Words.HelpKey),
                phone("phone.auth_check", Words.HelpAuthCheck, "S2"), phone("phone.dashboard", Words.HelpDashboard, "S2"))
            LocalHelpTopic.MODEL -> listOf(phone("phone.auto_model", Words.HelpAutoModel), phone("phone.model_status", Words.HelpModelStatus),
                phone("phone.model_lock", Words.HelpModelLock), phone("phone.model_limits", Words.HelpModelLimits))
            LocalHelpTopic.HISTORY -> listOf(phone("phone.chat_profile", Words.HelpChatProfile), phone("phone.chat_history", Words.HelpChatHistory),
                phone("phone.chat_readonly", Words.HelpChatReadOnly), phone("phone.chat_receipts", Words.HelpChatReceipts))
            LocalHelpTopic.PROJECT -> listOf(phone("phone.project_open", Words.HelpProjectOpen), phone("phone.project_details", Words.HelpProjectDetails),
                LocalHelpCheck("worker.project_files", Words.HelpProjectWorker, "S1", LocalHelpScope.EXECUTION_WORKER), phone("phone.project_limits", Words.HelpProjectLimits))
            LocalHelpTopic.OFFLINE_MODEL -> listOf(phone("phone.local_get", Words.PhoneOfflineSetupGetHelp),
                phone("phone.local_storage", Words.HelpOfflineStorage), phone("phone.local_memory", Words.HelpOfflineMemory),
                phone("phone.local_context", Words.HelpOfflineContext))
        }
    }
    fun messages(topic: LocalHelpTopic, observations: LocalHelpObservations?): List<LocalChatMessage> {
        val choices = checks(topic, observations)
        val facts = choices.joinToString("\n") {"${it.id} | ${it.scope} | [${it.sourceId}] ${it.word.en}"}
        require(facts.toByteArray().size <= 6000)
        val observed = observations?.let {
            "Phone preferences observed at ${it.capturedAt} epoch milliseconds: Demo=${it.demo}; API address kind=${it.address}; 64-bit process=${it.process64Bit}; local model loaded=${it.modelLoaded}. No server readiness or authentication was checked."
        } ?: "No phone observations were shared. Server state is unknown."
        val example = choices.take(3).joinToString(",", "[", "]") {"\"${it.id}\""}
        // IDs are language-independent. The UI renders reviewed translations after validation.
        val question = "Choose one to three relevant checks for ${topic.title.en}. Return only a JSON array of check IDs. No explanation, commands or rewritten instructions."
        return listOf(LocalChatMessage("system", "You rank reviewed Signal Grove setup checks. Use only IDs from the supplied choices. Return a JSON array of one to three unique IDs, nothing else. Never invent a check or claim actions were performed. Observations are limited phone preferences; server state is unknown. The app displays the approved text and execution location, not your prose. Valid output shape: $example"),
            LocalChatMessage("user", "Guide revision: $VERSION\nApproved choices:\n$facts\n\n$observed\n\n$question"))
    }

    /** Only reviewed facts can cross from model output to the helper UI. No generated instructions. */
    fun selection(text: String, topic: LocalHelpTopic, observations: LocalHelpObservations? = null): List<LocalHelpCheck>? {
        if(text.toByteArray().size !in 2..1024) return null
        val array = runCatching {Json.parseToJsonElement(text) as? JsonArray}.getOrNull() ?: return null
        if(array.size !in 1..3) return null
        val ids = array.map {item -> (item as? JsonPrimitive)?.takeIf {it.isString}?.content ?: return null}
        if(ids.distinct().size != ids.size) return null
        val allowed = checks(topic, observations).associateBy {it.id}
        return ids.map {allowed[it] ?: return null}
    }
}
