package com.vasmarfas.networkhelm.openwrt

import com.vasmarfas.networkhelm.model.DeviceException
import com.vasmarfas.networkhelm.model.ErrorCategory
import com.vasmarfas.networkhelm.ssh.SshSession

// the ACL of LuCI has neither subscriptions nor network.interface up, so root over HTTP needs this group
suspend fun installAcl(ssh: SshSession) {
    val script = "cat > $ACL_FILE <<'EOF'\n$ACL\nEOF\n" +
        "grep -qx $ACL_FILE /etc/sysupgrade.conf || echo $ACL_FILE >> /etc/sysupgrade.conf\n" +
        "/etc/init.d/rpcd reload\n"
    val result = ssh.exec(script)
    if (result.exitStatus != 0) throw DeviceException(ErrorCategory.General, "the ACL was not laid on the router: ${result.error.ifBlank { result.output }}")
}

private const val ACL_FILE = "/usr/share/rpcd/acl.d/networkhelm.json"

internal val ACL = """
{
	"networkhelm": {
		"description": "NetworkHelm client",
		"read": {
			"ubus": { "*": [ "*" ] },
			"uci": [ "*" ],
			"file": { "/*": [ "read", "list", "stat" ] }
		},
		"write": {
			"ubus": { "*": [ "*" ] },
			"uci": [ "*" ],
			"file": { "/*": [ "write", "exec" ] }
		}
	}
}
""".trim()
