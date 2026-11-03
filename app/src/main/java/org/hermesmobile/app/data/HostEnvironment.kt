package org.hermesmobile.app.data

import kotlinx.serialization.json.*
import java.util.Collections

class HostEnvUnavailable:Exception()
class HostEnvChanged:Exception()
class HostEnvUncertain:Exception()
class HostEnvReadbackPending:Exception()
enum class HostEnvAction { Save, Empty, Remove }
data class HostEnvProvider(val id:String,val label:String)
class HostEnvRow(val key:String,val isSet:Boolean,val description:String,val category:String,val password:Boolean?,val channel:Boolean?,val custom:Boolean?,val primary:Boolean?,providers:List<HostEnvProvider>,tools:List<String>) {
    val providers:List<HostEnvProvider> = Collections.unmodifiableList(providers.toList())
    val tools:List<String> = Collections.unmodifiableList(tools.toList())
    fun same(other:HostEnvRow)=key==other.key && isSet==other.isSet && description==other.description && category==other.category && password==other.password && channel==other.channel && custom==other.custom && primary==other.primary && providers==other.providers && tools==other.tools
    override fun toString()="HostEnvRow(redacted)"
}
class HostEnvInventory(val owner:ManagementTarget,val homeId:String,rows:List<HostEnvRow>) {
    val rows:List<HostEnvRow> = Collections.unmodifiableList(rows.toList())
    override fun toString()="HostEnvInventory(redacted)"
    companion object {
        fun parse(raw:JsonObject,owner:ManagementTarget,home:PluginHome?):HostEnvInventory=try {
            require(raw.size<=2000);val rows=raw.map {(key,value)->
                require(key.isNotBlank() && key.length<=256 && key.none(Char::isISOControl));val row=value.jsonObject
                fun text(j:JsonObject,k:String,max:Int):String {val v=j[k];require(v==null || v is JsonNull || v is JsonPrimitive && v.isString);return (v as? JsonPrimitive)?.contentOrNull.orEmpty().also {require(it.length<=max && '\u0000' !in it)}}
                fun flag(k:String):Boolean? {val v=row[k];require(v==null || v is JsonNull || v==JsonPrimitive(true) || v==JsonPrimitive(false));return (v as? JsonPrimitive)?.booleanOrNull}
                val present=flag("is_set") ?: error("Missing presence")
                val shared=row["provider_profiles"];require(shared==null || shared is JsonNull || shared is JsonArray && shared.size<=64)
                val providers=(shared as? JsonArray).orEmpty().map {v->val p=v.jsonObject;val id=text(p,"provider",128);require(id.isNotBlank() && id.none(Char::isISOControl));HostEnvProvider(id,text(p,"provider_label",512))}.toMutableList()
                val provider=text(row,"provider",128);if(provider.isNotBlank() && providers.none {it.id==provider}) providers.add(HostEnvProvider(provider,text(row,"provider_label",512)));require(providers.map {it.id}.distinct().size==providers.size)
                val tools=row["tools"];require(tools==null || tools is JsonNull || tools is JsonArray && tools.size<=256)
                val names=(tools as? JsonArray).orEmpty().map {v->require(v is JsonPrimitive && v.isString && v.content.length<=512 && v.content.none(Char::isISOControl));v.content}
                HostEnvRow(key,present,text(row,"description",8192),text(row,"category",128),flag("is_password"),flag("channel_managed"),flag("custom"),flag("provider_primary"),providers,names)
            }
            HostEnvInventory(owner,home?.identity() ?: error("Profile home unavailable"),rows.sortedBy {it.key})
        } catch(e:Exception) {throw HostEnvUnavailable()}
    }
}
object HostEnvPolicy {
    // Reviewed v0.22.6 generic writer policy; host managed and dynamic launch policy still applies.
    private val denied=setOf("BASH_ENV","BROWSER","CLASSPATH","DYLD_FALLBACK_FRAMEWORK_PATH","DYLD_FALLBACK_LIBRARY_PATH","DYLD_FRAMEWORK_PATH","DYLD_INSERT_LIBRARIES","DYLD_LIBRARY_PATH","EDITOR","ENV","EXINIT","GIT_ASKPASS","GIT_DIR","GIT_EDITOR","GIT_EXEC_PATH","GIT_EXTERNAL_DIFF","GIT_PAGER","GIT_PROXY_COMMAND","GIT_SEQUENCE_EDITOR","GIT_SHELL","GIT_SSH","GIT_SSH_COMMAND","GIT_TEMPLATE_DIR","GOFLAGS","HERMES_ACCEPT_HOOKS","HERMES_CONFIG","HERMES_CONFIG_PATH","HERMES_CRON_SESSION","HERMES_ENV","HERMES_ENV_PATH","HERMES_EXEC_ASK","HERMES_GATEWAY_SESSION","HERMES_HOME","HERMES_INTERACTIVE","HERMES_OPTIONAL_MCPS","HERMES_PROFILE","HERMES_REDACT_SECRETS","HERMES_SESSION_KEY","HERMES_SESSION_PLATFORM","HERMES_SINGLE_QUERY_SESSION","HERMES_YOLO_MODE","JAVA_TOOL_OPTIONS","JDK_JAVA_OPTIONS","LD_AUDIT","LD_DEBUG","LD_LIBRARY_PATH","LD_PRELOAD","MANPAGER","NODE_OPTIONS","NODE_PATH","PAGER","PATH","PERL5LIB","PERL5OPT","PERLLIB","PROMPT_COMMAND","PYTHONBREAKPOINT","PYTHONCASEOK","PYTHONEXECUTABLE","PYTHONHOME","PYTHONNOUSERSITE","PYTHONPATH","PYTHONSTARTUP","PYTHONUSERBASE","RUBYLIB","RUBYOPT","RUSTFLAGS","SHELL","SSH_ASKPASS","SUDO_ASKPASS","VIMINIT","VISUAL","ZDOTDIR","_JAVA_OPTIONS")
    private val prefixes=listOf("DYLD_","GIT_CONFIG_","LD_")
    fun key(key:String)=key.matches(Regex("[A-Za-z_][A-Za-z0-9_]{0,255}"))
    fun writable(key:String)=key(key) && key.uppercase().let {it !in denied && prefixes.none(it::startsWith)}
    fun value(value:String)=value.isNotEmpty() && value.length<=16384 && value.all {it.code in 32..126} && !value.startsWith("«redacted") && value!="***" && !Regex(".{4}\\.\\.\\..{4}").matches(value)
}
class HostEnvRequest(val expected:HostEnvInventory,val key:String,val action:HostEnvAction,val value:String="") {
    val previous=expected.rows.singleOrNull {it.key==key}
    init {require(HostEnvPolicy.key(key));require(when(action) {HostEnvAction.Save->HostEnvPolicy.writable(key) && HostEnvPolicy.value(value);HostEnvAction.Empty->HostEnvPolicy.writable(key) && value.isEmpty();HostEnvAction.Remove->previous!=null && value.isEmpty()})}
    val receiptAction get()=when(action) {HostEnvAction.Save->"EnvSave";HostEnvAction.Empty->"EnvEmpty";HostEnvAction.Remove->"EnvRemove"}
    fun body(profile:String)=buildJsonObject {GatewayCronInventory.requireProfile(profile);require(profile==expected.owner.profile);put("profile",profile);put("key",key);if(action!=HostEnvAction.Remove) {put("value",value);put("provider_setup",false)}}
    fun same(current:HostEnvInventory)=expected.owner==current.owner && expected.homeId==current.homeId && current.rows.singleOrNull {it.key==key}.let {now->if(previous==null) now==null else now?.let(previous::same)==true}
    fun summary()=buildJsonObject {put("env_key",key);put("operation",action.name);put("previous_presence",previous?.isSet?.let(::JsonPrimitive) ?: JsonNull);put("custom",previous?.custom?.let(::JsonPrimitive) ?: JsonNull)}
    fun acknowledge(raw:JsonObject):JsonObject {
        require(raw["ok"]==JsonPrimitive(true) && raw["key"]==JsonPrimitive(key))
        if(action==HostEnvAction.Remove) require(raw["found"]==JsonPrimitive(true) && (raw["removed"]==JsonPrimitive(true) || raw["removed"]==JsonPrimitive(false)))
        fun count(name:String):Int?=runCatching {val values=raw[name] as? JsonArray ?: return@runCatching null;require(values.size<=1024 && values.all {it is JsonPrimitive && it.isString && it.content.length<=2048});values.size}.getOrNull()
        return buildJsonObject {summary().forEach {(k,v)->put(k,v)};if(action==HostEnvAction.Remove) put("env_entry_removed",raw["removed"]!!);for(name in listOf("config_updates","config_scrubbed","pool_pruned","providers")) count(name)?.let {put(name+"_count",it)}}
    }
    fun presenceMatches(after:HostEnvInventory)=when(action) {HostEnvAction.Save->after.rows.singleOrNull {it.key==key}?.isSet==true;HostEnvAction.Empty->after.rows.singleOrNull {it.key==key}?.isSet==false;HostEnvAction.Remove->after.rows.none {it.key==key && it.isSet}}
    override fun toString()="HostEnvRequest(redacted)"
}
