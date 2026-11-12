package org.hermesmobile.app.local

/** A bounded set of reviewed IDs, never a caller-supplied grammar or instructions. */
class LocalCheckChoices private constructor(val ids: List<String>) {
    companion object {
        fun from(ids: List<String>): LocalCheckChoices {
            require(ids.size in 1..5 && ids.distinct().size == ids.size)
            require(ids.all { it.matches(Regex("[a-z0-9_.]{1,64}")) })
            return LocalCheckChoices(java.util.Collections.unmodifiableList(ArrayList(ids)))
        }
    }
}
