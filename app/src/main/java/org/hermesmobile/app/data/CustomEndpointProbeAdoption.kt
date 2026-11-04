package org.hermesmobile.app.data

enum class CustomEndpointProbeAdoptionKind {Url,Mode,Catalog,Model}

/** Local draft staging only. The frozen report, owner and full prior draft must still match. */
class CustomEndpointProbeAdoption private constructor(val report:CustomEndpointProbeReport,val kind:CustomEndpointProbeAdoptionKind,private val before:CustomEndpointDraft,val draft:CustomEndpointDraft) {
    val changes get()=before.body()!=draft.body()
    fun stillCurrent(inventory:CustomEndpointSnapshot,target:ManagementTarget?,liveReport:CustomEndpointProbeReport?,current:CustomEndpointDraft)=
        liveReport===report && inventory===report.request.expected && target==inventory.owner && report.request.same(current) && before.body()==current.body()
    override fun toString()="CustomEndpointProbeAdoption(redacted)"
    companion object {
        fun create(report:CustomEndpointProbeReport,before:CustomEndpointDraft,kind:CustomEndpointProbeAdoptionKind,model:String?=null):CustomEndpointProbeAdoption?=runCatching {
            val owner=report.request.expected.owner;val receipt=report.receipt
            require(receipt.action=="CustomEndpointProbe" && receipt.state=="acknowledged" && receipt.apiScope==owner.apiScope && receipt.resourceScope==owner.resourceScope && receipt.url==owner.url && receipt.profile==owner.profile && receipt.actor==owner.actor)
            require(report.request.same(before))
            val details=report.details ?: error("No usable report details.")
            var url=before.baseUrl;var mode=before.apiMode;var selected=before.model;var key=before.key;var secret=before.secret;var models=before.models;var metadata=before.modelDetails
            when(kind) {
                CustomEndpointProbeAdoptionKind.Url->{
                    url=details.resolvedBaseUrl?.trim()?.trimEnd('/') ?: error("No returned address.")
                    require(CustomEndpointPolicy.route(url,selected))
                    if(url!=before.baseUrl) {if(key==CustomEndpointKey.Fresh) key=CustomEndpointKey.Keep;secret="";metadata=emptyList()}
                }
                CustomEndpointProbeAdoptionKind.Mode->{mode=details.transport?.trim() ?: error("No returned mode.");require(mode in HostFallbackPolicy.apiModes)}
                CustomEndpointProbeAdoptionKind.Catalog->{
                    val ids=details.models.map {it.trim()}
                    require(ids.isNotEmpty() && ids.size<=256 && ids.distinct().size==ids.size && ids.all {CustomEndpointPolicy.field(it,2048,true)})
                    models=(before.models+ids).distinct()
                }
                CustomEndpointProbeAdoptionKind.Model->{
                    selected=model?.trim() ?: error("No selected model.")
                    require(CustomEndpointPolicy.field(selected,2048,true) && (details.models.any {it.trim()==selected} || details.details.any {it.id.trim()==selected}))
                    val matches=details.details.filter {it.id.trim()==selected};require(matches.size<=1)
                    matches.singleOrNull()?.let {row->metadata=before.modelDetails.filter {it.id!=selected}+CustomEndpointModelDetail(selected,row.canonicalModel,row.reasoningEffort)}
                    // Retain previously staged metadata when another model becomes selected.
                    models=(before.models+metadata.map {it.id}.filter {it!=selected}).distinct()
                }
            }
            require(models.joinToString("\n").length<=131072)
            val after=CustomEndpointDraft(before.id,before.name,url,selected,key,secret,mode,before.contextLength,before.discoverModels,before.makeDefault,models,metadata)
            require(after.body().toString().length<=600_000)
            if(kind in setOf(CustomEndpointProbeAdoptionKind.Catalog,CustomEndpointProbeAdoptionKind.Model)) require(report.request.expected.canMergeModels(after))
            CustomEndpointProbeAdoption(report,kind,before,after)
        }.getOrNull()
    }
}
