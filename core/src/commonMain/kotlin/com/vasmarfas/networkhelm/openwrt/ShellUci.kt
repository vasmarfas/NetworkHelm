package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.ssh.SshSession

// uci -P commits nothing and sees the console drafts in /tmp/.uci too, so apply writes /etc/config itself
class ShellUci(private val ssh: SshSession) : Uci {

    override suspend fun configs(): List<String> = run("ls /etc/config").lines().filter { it.isNotBlank() }

    override suspend fun sections(config: String): List<UciSection> {
        val names = run("uci -P $DRAFT -X show ${quote(config)}").lines()
            .map { it.substringBefore('=') }
            .filter { it.count { char -> char == '.' } == 1 }
            .map { it.substringAfter('.') }
        val sections = mutableListOf<Exported>()
        for (line in run("uci -P $DRAFT export ${quote(config)}").lines()) {
            val words = uciWords(line)
            when (words.firstOrNull()) {
                "config" -> sections += Exported(words.getOrElse(1) { "" }, words.size < 3)
                "option" -> sections.lastOrNull()?.options?.put(words[1], words.getOrElse(2) { "" })
                "list" -> sections.lastOrNull()?.lists?.getOrPut(words[1]) { mutableListOf() }?.add(words.getOrElse(2) { "" })
            }
        }
        return names.zip(sections) { name, section -> UciSection(name, section.type, section.anonymous, section.options, section.lists) }
    }

    override suspend fun add(config: String, type: String, name: String?, options: Map<String, String>, lists: Map<String, List<String>>): String {
        val section = if (name != null) {
            batch(listOf("set $config.$name=${quote(type)}"))
            name
        } else {
            run("uci -P $DRAFT add ${quote(config)} ${quote(type)}").trim()
        }
        set(config, section, options, lists)
        return section
    }

    override suspend fun set(config: String, section: String, options: Map<String, String>, lists: Map<String, List<String>>) {
        batch(
            options.map { (option, value) -> "set $config.$section.$option=${quote(value)}" } +
                lists.flatMap { (list, values) -> listOf("delete $config.$section.$list") + values.map { "add_list $config.$section.$list=${quote(it)}" } },
        )
    }

    override suspend fun delete(config: String, section: String, options: List<String>?) {
        batch(options?.map { "delete $config.$section.$it" } ?: listOf("delete $config.$section"))
    }

    override suspend fun order(config: String, sections: List<String>) {
        batch(sections.mapIndexed { index, section -> "reorder $config.$section=$index" })
    }

    override suspend fun changes(): List<UciChange> = run("uci -P $DRAFT changes").lines().filter { it.isNotBlank() }.map { line ->
        val removal = line.startsWith("-")
        val path = line.removePrefix("-").substringBefore('=').removeSuffix("+").split('.')
        val operation = when {
            removal -> "remove"
            "+=" in line -> "list-add"
            else -> "set"
        }
        UciChange(path[0], operation, path.getOrElse(1) { "" }, path.getOrNull(2), if (removal) null else uciWords(line.substringAfter('=')).firstOrNull())
    }

    override suspend fun revert(config: String) {
        run("uci -P $DRAFT revert ${quote(config)}")
    }

    override suspend fun apply(rollbackSeconds: Int) {
        run(
            """
            [ -d $APPLY ] && { echo "another apply waits for its confirmation"; exit ${UbusStatus.PERMISSION_DENIED}; }
            configs=""
            for draft in $DRAFT/*; do [ -s "${'$'}draft" ] && configs="${'$'}configs ${'$'}{draft##*/}"; done
            [ -n "${'$'}configs" ] || exit ${UbusStatus.NO_DATA}
            for c in ${'$'}configs; do
                [ -s "/tmp/.uci/${'$'}c" ] && { echo "the console of the router has edits of ${'$'}c that are not committed"; exit ${UbusStatus.PERMISSION_DENIED}; }
            done
            mkdir -p $APPLY
            for c in ${'$'}configs; do
                cp "/etc/config/${'$'}c" "$APPLY/${'$'}c.old"
                cp "$DRAFT/${'$'}c" "$APPLY/${'$'}c.draft"
                uci -P $DRAFT export "${'$'}c" | sed '1{/^package /d}' > "$APPLY/${'$'}c.new"
            done
            for c in ${'$'}configs; do
                cat "$APPLY/${'$'}c.new" > "/etc/config/${'$'}c"
                rm -f "$DRAFT/${'$'}c"
            done
            reload_config
            ( trap '' HUP; sleep $rollbackSeconds; [ -d $APPLY ] && { $RESTORE; } ) </dev/null >/dev/null 2>&1 &
            echo ${'$'}! > $APPLY/pid
            """.trimIndent(),
        )
    }

    override suspend fun confirm() {
        run("[ -d $APPLY ] || exit ${UbusStatus.NO_DATA}; kill \$(cat $APPLY/pid) 2>/dev/null; rm -rf $APPLY")
    }

    override suspend fun rollback() {
        run("[ -d $APPLY ] || exit ${UbusStatus.NO_DATA}; kill \$(cat $APPLY/pid) 2>/dev/null; $RESTORE")
    }

    private suspend fun batch(commands: List<String>) {
        if (commands.isNotEmpty()) run("printf '%s\\n' ${quote(commands.joinToString("\n"))} | uci -P $DRAFT batch")
    }

    private suspend fun run(command: String): String {
        val result = ssh.exec("mkdir -p $DRAFT; $command")
        if (result.exitStatus != 0) {
            val printed = (result.output + result.error).trim()
            val status = when {
                result.exitStatus == UbusStatus.NO_DATA || result.exitStatus == UbusStatus.PERMISSION_DENIED -> result.exitStatus
                printed.endsWith("Entry not found") -> UbusStatus.NOT_FOUND
                else -> UbusStatus.UNKNOWN_ERROR
            }
            throw UbusException(status, printed.ifEmpty { "uci exited with ${result.exitStatus}" })
        }
        return result.output
    }

    private class Exported(val type: String, val anonymous: Boolean) {
        val options = mutableMapOf<String, String>()
        val lists = mutableMapOf<String, MutableList<String>>()
    }

    private companion object {
        const val DRAFT = "/tmp/rd-uci"
        const val APPLY = "/tmp/rd-apply"

        const val RESTORE = "for old in $APPLY/*.old; do c=\${old##*/}; c=\${c%.old}; cat \"\$old\" > \"/etc/config/\$c\"; " +
            "cp \"$APPLY/\$c.draft\" \"$DRAFT/\$c\"; done; rm -rf $APPLY; reload_config"
    }
}
