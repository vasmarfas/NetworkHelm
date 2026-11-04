package com.vasmarfas.networkhelm.routeros

data class RouterOsVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
    val stage: Int = RELEASE,
    val stageNumber: Int = 0,
) : Comparable<RouterOsVersion> {

    override fun compareTo(other: RouterOsVersion) =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.stageNumber })

    fun within(since: String?, until: String?): Boolean {
        val from = since?.let { parse(it) }
        val to = until?.let { parse(it) }
        return (from == null || this >= from) && (to == null || this < to)
    }

    companion object {
        private const val RELEASE = 3
        private val stages = listOf("alpha", "beta", "rc")
        private val pattern = Regex("""^\s*(\d+)\.(\d+)(?:\.(\d+))?(?:(alpha|beta|rc)(\d+))?""")

        fun parse(text: String): RouterOsVersion? {
            val (major, minor, patch, stage, number) = pattern.find(text)?.destructured ?: return null
            return RouterOsVersion(
                major.toInt(),
                minor.toInt(),
                patch.toIntOrNull() ?: 0,
                stages.indexOf(stage).takeIf { it >= 0 } ?: RELEASE,
                number.toIntOrNull() ?: 0,
            )
        }
    }
}
