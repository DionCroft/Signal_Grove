package org.hermesmobile.app.updates

import kotlinx.serialization.json.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Collections

class UpdateUnavailable:Exception()
class UpdateIntegrityFailure:Exception()
class UpdateSignatureFailure:Exception()
class UpdatePackageFailure:Exception()
class UpdateVersionFailure:Exception()
class UpdateAndroidFailure:Exception()
class UpdateRateLimited:Exception()

class AppVersion private constructor(val text:String,private val numbers:List<Int>,private val prerelease:List<String>):Comparable<AppVersion> {
    val preview get()=prerelease.isNotEmpty()
    override fun compareTo(other:AppVersion):Int {
        numbers.zip(other.numbers).forEach {(a,b)->if(a!=b) return a.compareTo(b)}
        if(prerelease.isEmpty() || other.prerelease.isEmpty()) return when {prerelease.isEmpty() && other.prerelease.isEmpty()->0;prerelease.isEmpty()->1;else->-1}
        prerelease.zip(other.prerelease).forEach {(a,b)->if(a!=b) {val an=a.all(Char::isDigit);val bn=b.all(Char::isDigit);return when {an && bn->if(a.length!=b.length) a.length.compareTo(b.length) else a.compareTo(b);an->-1;bn->1;else->a.compareTo(b)}}}
        return prerelease.size.compareTo(other.prerelease.size)
    }
    companion object {
        fun parse(raw:String):AppVersion?=runCatching {
            require(raw.length<=96)
            val match=Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?").matchEntire(raw) ?: error("Invalid version")
            val parts=(1..3).map {match.groupValues[it].toInt().also {n->require(n<=1_000_000)}}
            val pre=match.groupValues[4].takeIf {it.isNotEmpty()}?.split('.') ?: emptyList()
            require(pre.all {it.length<=32 && (!it.all(Char::isDigit) || it=="0" || !it.startsWith('0'))})
            AppVersion(raw,parts,pre)
        }.getOrNull()
    }
}
interface UpdateEndpointPolicy {
    fun metadata(previews:Boolean):HttpUrl
    fun asset(url:HttpUrl,tag:String,name:String):Boolean
    fun redirect(url:HttpUrl):Boolean
}
object OfficialUpdateEndpoints:UpdateEndpointPolicy {
    const val REPO="DionCroft/Signal_Grove"
    override fun metadata(previews:Boolean)=("https://api.github.com/repos/$REPO/releases"+if(previews) "?per_page=20" else "/latest").toHttpUrl()
    override fun asset(url:HttpUrl,tag:String,name:String)=url.scheme=="https" && url.host=="github.com" && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null && url.pathSegments==listOf("DionCroft","Signal_Grove","releases","download",tag,name)
    override fun redirect(url:HttpUrl)=url.scheme=="https" && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.fragment==null && url.host in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com","github-releases.githubusercontent.com")
}
class AppRelease(val id:Long,val tag:String,val version:AppVersion,val notes:String,val assetName:String?,val assetUrl:HttpUrl?,val bytes:Long?,val sha256:String?,val previews:Boolean) {
    val downloadable get()=assetName!=null && assetUrl!=null && bytes!=null && sha256!=null
    val key get()="$id:${sha256.orEmpty()}:${version.text}"
    val page get()=("https://github.com/"+OfficialUpdateEndpoints.REPO+"/releases/tag/").toHttpUrl().newBuilder().addPathSegment(tag).build().toString()
    fun json()=buildJsonObject {put("id",id);put("tag",tag);put("version",version.text);put("notes",notes);put("asset_name",assetName);put("asset_url",assetUrl?.toString());put("bytes",bytes);put("sha256",sha256);put("previews",previews)}
    companion object {
        const val MAX_APK_BYTES=128L*1024*1024
        fun select(payload:JsonElement,current:AppVersion,debug:Boolean,previews:Boolean,policy:UpdateEndpointPolicy=OfficialUpdateEndpoints):AppRelease? {
            val rows=if(payload is JsonArray) payload.also {require(it.size<=20)} else JsonArray(listOf(payload))
            val versions=rows.mapNotNull {v->
                val j=v as? JsonObject ?: throw UpdateUnavailable()
                fun flag(key:String):Boolean {val x=j[key];require(x is JsonPrimitive && !x.isString);return x.booleanOrNull ?: throw UpdateUnavailable()}
                if(flag("draft")) return@mapNotNull null
                val tag=(j["tag_name"] as? JsonPrimitive)?.takeIf {it.isString}?.content ?: throw UpdateUnavailable()
                val version=AppVersion.parse(tag.removePrefix("v")) ?: return@mapNotNull null
                val preview=flag("prerelease") || version.preview
                if(preview && !previews || version<=current) return@mapNotNull null
                Triple(j,tag,version)
            }
            val latest=versions.maxByOrNull {it.third} ?: return null
            val (j,tag,version)=latest
            val id=j["id"].let {require(it is JsonPrimitive && !it.isString);it.longOrNull?.takeIf {n->n>0} ?: throw UpdateUnavailable()}
            val body=j["body"].let {if(it==null || it==JsonNull) "" else {require(it is JsonPrimitive && it.isString);it.content.take(6000)}}
            val name="hermes-agent-mobile-${version.text}"+(if(debug) "-debug" else "")+".apk"
            val assets=j["assets"] as? JsonArray ?: throw UpdateUnavailable();require(assets.size<=50)
            val matches=assets.filterIsInstance<JsonObject>().filter {(it["name"] as? JsonPrimitive)?.content==name}
            val asset=matches.singleOrNull()
            val url=asset?.get("browser_download_url")?.let {runCatching {require(it is JsonPrimitive && it.isString);it.content.toHttpUrl()}.getOrNull()}?.takeIf {policy.asset(it,tag,name)}
            val size=asset?.get("size")?.let {if(it is JsonPrimitive && !it.isString) it.longOrNull else null}?.takeIf {it in 1..MAX_APK_BYTES}
            val digest=(asset?.get("digest") as? JsonPrimitive)?.takeIf {it.isString}?.content?.takeIf {it.matches(Regex("sha256:[a-fA-F0-9]{64}"))}?.substring(7)?.lowercase()
            val good=asset?.get("state")==JsonPrimitive("uploaded") && matches.size==1 && url!=null && size!=null && digest!=null
            return AppRelease(id,tag,version,body,if(good) name else null,if(good) url else null,if(good) size else null,if(good) digest else null,previews)
        }
        fun restore(j:JsonObject,debug:Boolean):AppRelease {
            val version=AppVersion.parse(j.getValue("version").jsonPrimitive.content) ?: throw UpdateUnavailable()
            val tag=j.getValue("tag").jsonPrimitive.content;require(tag.removePrefix("v")==version.text)
            val id=j.getValue("id").jsonPrimitive.long;require(id>0)
            val notes=j.getValue("notes").jsonPrimitive.content.take(6000)
            val previews=j.getValue("previews").jsonPrimitive.boolean
            if(listOf("asset_name","asset_url","bytes","sha256").all {j[it]==JsonNull}) return AppRelease(id,tag,version,notes,null,null,null,null,previews)
            val name=j.getValue("asset_name").jsonPrimitive.content;require(name=="hermes-agent-mobile-${version.text}"+(if(debug) "-debug" else "")+".apk")
            val url=j.getValue("asset_url").jsonPrimitive.content.toHttpUrl();require(OfficialUpdateEndpoints.asset(url,tag,name))
            val bytes=j.getValue("bytes").jsonPrimitive.long;require(bytes in 1..MAX_APK_BYTES)
            val sha=j.getValue("sha256").jsonPrimitive.content;require(sha.matches(Regex("[a-f0-9]{64}")))
            return AppRelease(id,tag,version,notes,name,url,bytes,sha,previews)
        }
    }
}
