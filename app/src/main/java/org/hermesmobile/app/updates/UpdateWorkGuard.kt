package org.hermesmobile.app.updates

import kotlinx.coroutines.flow.MutableStateFlow

/** Only activity flags are shared; no connection, actor or private request data enters this guard. */
class UpdateWorkGuard {
    private val owners=mutableSetOf<Any>()
    val active=MutableStateFlow(false)
    @Synchronized fun set(owner:Any,busy:Boolean) {if(busy) owners.add(owner) else owners.remove(owner);active.value=owners.isNotEmpty()}
}
