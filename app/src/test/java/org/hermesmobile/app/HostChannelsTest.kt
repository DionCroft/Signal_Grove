package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class HostChannelsTest {
    private val owner=ManagementTarget("a".repeat(64),"https://host.test/prefix/","worker","basic · fixture")
    private fun field(key:String="BOT_TOKEN",present:Boolean=true,password:Boolean=true,list:Boolean=false,value:JsonElement=JsonPrimitive("discarded-stored-secret-private"))=buildJsonObject {put("key",key);put("required",true);put("is_set",present);put("is_password",password);put("is_list",list);put("prompt","Fixture field");put("description","Description");put("value",value);put("redacted_value","discarded-preview-private")}
    private fun row(fields:List<JsonObject> = listOf(field(),field("ALLOWED_USERS",false,false,true,JsonPrimitive("用户一,123"))),enabled:Boolean=false)=buildJsonObject {
        put("id","Plugin.Relay:1");put("name","Fixture channel");put("description","Description");put("docs_url","https://docs.test/channel");put("enabled",enabled);put("configured",true);put("gateway_running",false);put("state","gateway_stopped");put("env_vars",JsonArray(fields));put("home_channel",buildJsonObject {put("platform","plugin");put("chat_id","private-chat");put("name","Private home");put("unknown_secret","discard-home-private")})
    }
    private fun raw(row:JsonObject=row())=buildJsonObject {put("platforms",buildJsonArray {add(row)});put("env_path","discard-path-private");put("gateway_start_command","discard-command-private")}
    private fun inventory(row:JsonObject=row(),target:ManagementTarget=owner,home:String="home-fixture")=HostChannelInventory.parse(raw(row),target,home)
    private fun request(draft:HostChannelDraft?=HostChannelDraft(true))=HostChannelRequest(inventory(),"Plugin.Relay:1",draft)
    private fun fails(block:()->Unit)=assertTrue(runCatching(block).isFailure)

    @Test fun onlyDeclaredPrivateListValuesAndKnownHomeFieldsSurviveProjection() {
        val inv=inventory();val channel=inv.rows.single();assertNull(channel.fields.first().savedList);assertEquals("用户一,123",channel.fields.last().savedList)
        assertEquals(setOf("platform","chat_id","name"),channel.home!!.keys)
        for(text in listOf(inv.toString(),channel.toString(),channel.fields.joinToString())) for(secret in listOf("discarded-stored-secret-private","discarded-preview-private","private-chat","用户一","discard-path-private","discard-command-private")) assertFalse(text.contains(secret))
    }
    @Test fun malformedRequiredMetadataDoesNotBecomeAnEmptyOrDisabledCatalog() {
        fails {HostChannelInventory.parse(buildJsonObject {put("platforms",17)},owner,"home")}
        for(key in listOf("enabled","configured","gateway_running","env_vars","state")) fails {inventory(JsonObject(row()-key))}
        fails {inventory(JsonObject(row()+mapOf("enabled" to JsonPrimitive("true"))))}
        for(key in listOf("is_set","is_password","is_list")) fails {inventory(row(listOf(JsonObject(field()-key))))}
        fails {inventory(row(listOf(field(password=true,list=true))))}
    }
    @Test fun duplicatePlatformsFieldsAndOversizedValuesAreRefusedWithoutTruncation() {
        fails {HostChannelInventory.parse(buildJsonObject {put("platforms",buildJsonArray {add(row());add(row())})},owner,"home")}
        fails {inventory(row(listOf(field(),field())))}
        fails {inventory(row((0..128).map {field("K"+it)}))}
        fails {inventory(row(listOf(field("LIST",false,false,true,JsonPrimitive("x".repeat(16385))))))}
    }
    @Test fun exactBodyTrimsReplacementAndOmitsKeptEnablementAndUnchangedFields() {
        val r=request(HostChannelDraft(replacements=mapOf("ALLOWED_USERS" to " 用户二,456 "),clears=listOf("BOT_TOKEN")))
        assertEquals(buildJsonObject {put("profile","worker");put("env",buildJsonObject {put("ALLOWED_USERS","用户二,456")});put("clear_env",buildJsonArray {add("BOT_TOKEN")})},r.body("worker"))
        fails {r.body("other")};fails {r.body("all")}
    }
    @Test fun noOpEnablementAndAmbiguousClearReplaceCannotBecomeSaves() {
        fails {request(HostChannelDraft(false))};fails {request(HostChannelDraft())}
        fails {HostChannelDraft(replacements=mapOf("BOT_TOKEN" to "new"),clears=listOf("BOT_TOKEN"))}
        fails {HostChannelDraft(clears=listOf("BOT_TOKEN","BOT_TOKEN"))}
        assertTrue(request(HostChannelDraft(clears=listOf("ALLOWED_USERS"))).draft!!.clears.isNotEmpty())
    }
    @Test fun onlyDeclaredWritableKeysAndFreshValidValuesCanBeSent() {
        fails {request(HostChannelDraft(replacements=mapOf("UNKNOWN" to "new")))}
        val guarded=inventory(row(listOf(field("PYTHONPATH"))));fails {HostChannelRequest(guarded,"Plugin.Relay:1",HostChannelDraft(clears=listOf("PYTHONPATH")))}
        for(value in listOf(""," ","***","abcd...efgh","«redacted key»","line\nnext","中文-token")) fails {request(HostChannelDraft(replacements=mapOf("BOT_TOKEN" to value)))}
        assertNotNull(request(HostChannelDraft(replacements=mapOf("ALLOWED_USERS" to "中文用户"))))
    }
    @Test fun callerCollectionsCannotRewriteFrozenDraftOrInventory() {
        val replacements=mutableMapOf("BOT_TOKEN" to "new-private");val clears=mutableListOf("ALLOWED_USERS");val d=HostChannelDraft(replacements=replacements,clears=clears);val r=request(d);val body=r.body("worker")
        replacements["BOT_TOKEN"]="changed";clears.clear();assertEquals(body,r.body("worker"))
        fails {(d.replacements as MutableMap<String,String>)["BOT_TOKEN"]="changed"};fails {(d.clears as MutableList<String>).clear()};fails {(r.expected.rows as MutableList<HostChannelRow>).clear()}
    }
    @Test fun privateHomeProjectionIsCopiedEvenForAnExplicitCallerConstructedRow() {
        val home=mutableMapOf<String,JsonElement>("chat_id" to JsonPrimitive("original"));val row=HostChannelRow("plugin","Plugin","","",false,false,false,"disabled",false,false,false,false,"",JsonObject(home),emptyList())
        home["chat_id"]=JsonPrimitive("changed");assertEquals(JsonPrimitive("original"),row.home!!["chat_id"])
    }
    @Test fun reviewBindsProfileHomeIdentityPublicMetadataAndPrivateAllowlist() {
        val r=request();assertTrue(r.same(inventory()))
        assertFalse(r.same(inventory(target=owner.copy(actor="basic · other"))));assertFalse(r.same(inventory(home="other-home")))
        assertFalse(r.same(inventory(row(enabled=true))))
        assertFalse(r.same(inventory(row(listOf(field(present=false),field("ALLOWED_USERS",false,false,true,JsonPrimitive("用户一,123")))))))
        assertFalse(r.same(inventory(row(listOf(field(),field("ALLOWED_USERS",false,false,true,JsonPrimitive("other-private")))))))
    }
    @Test fun transientHealthChangesDoNotChangeTheReviewedConfigurationProjection() {
        val updated=JsonObject(row()+mapOf("state" to JsonPrimitive("connected"),"gateway_running" to JsonPrimitive(true),"updated_at" to JsonPrimitive(123),"error_message" to JsonPrimitive("private-runtime-error")))
        assertTrue(request().same(inventory(updated)))
    }
    @Test fun observationsCheckOnlyReportedEnablementAndPresenceRatherThanSecretIdentity() {
        val r=request(HostChannelDraft(true,mapOf("BOT_TOKEN" to "new-secret"),listOf("ALLOWED_USERS")))
        val after=inventory(row(listOf(field(value=JsonPrimitive("different-hidden-secret")),field("ALLOWED_USERS",false,false,true,JsonNull)),enabled=true))
        assertTrue(r.observed(after));assertFalse(r.observed(inventory(row(enabled=false))))
        assertFalse(r.observed(inventory(row(listOf(field(present=false)),enabled=true))))
    }
    @Test fun typedSaveAckRequiresExactPlatformAndHotServeFlag() {
        val r=request();val raw=buildJsonObject {put("ok",true);put("platform",r.id);put("hot_served",false)}
        assertEquals(false,r.acknowledge(raw).hotServed)
        for(bad in listOf(JsonObject(raw-"hot_served"),JsonObject(raw+mapOf("platform" to JsonPrimitive("other"))),JsonObject(raw+mapOf("ok" to JsonPrimitive(false))))) fails {r.acknowledge(bad)}
    }
    @Test fun negativePostureIsAcknowledgedAndOptionalDetailFailureDoesNotUndoIt() {
        val r=request(null);val raw=buildJsonObject {put("ok",false);put("state","gateway_stopped");put("message",buildJsonObject {put("bad",true)})}
        val ack=r.acknowledge(raw);assertFalse(ack.ok);assertEquals(JsonPrimitive(true),ack.summary()["acknowledged"]);assertNull(HostChannelPostureDetails.parse(raw))
        fails {r.acknowledge(JsonObject(raw-"ok"))};fails {r.acknowledge(JsonObject(raw+mapOf("state" to JsonPrimitive(17))))}
    }
    @Test fun durableSummariesOmitNewValuesHomeIdsAllowlistAndHostMessages() {
        val r=request(HostChannelDraft(replacements=mapOf("BOT_TOKEN" to "new-secret-private")))
        val ack=r.acknowledge(buildJsonObject {put("ok",true);put("platform",r.id);put("hot_served",true);put("message","private-message")})
        val summary=r.summary().toString()+ack.summary()+r.toString()+r.draft.toString()
        for(value in listOf("new-secret-private","private-chat","用户一","discarded-stored-secret-private","private-message")) assertFalse(summary.contains(value))
        assertFalse(r.summary().containsKey("env"));assertEquals(JsonPrimitive(1),r.summary()["replacement_count"])
    }
    @Test fun literalPlatformIdsAndExplicitHttpsDocsHaveIndependentAddressabilityGuards() {
        for(value in listOf("Plugin.Relay:1","插件")) assertTrue(HostChannelPolicy.id(value))
        for(value in listOf(".","..","x/y","x\\y"," x","x?y","x\n")) assertFalse(HostChannelPolicy.id(value))
        assertTrue(HostChannelPolicy.browser("https://docs.test/help#setup"))
        for(value in listOf("javascript:alert(1)","http://docs.test/","https://user@docs.test/","file:///private")) assertFalse(HostChannelPolicy.browser(value))
    }
    @Test fun unsupportedProfileCannotConstructAnApprovedPostureRequest() {
        fails {HostChannelRequest(inventory(target=owner.copy(profile="all")),"Plugin.Relay:1")}
        fails {HostChannelDraft(replacements=(0..128).associate {"K"+it to "v"})}
    }
}
