package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.duration

private val ipChains = choice("chain", "prerouting", "input", "forward", "output", "postrouting")

private val ipProtocols = choice("protocol", "tcp", "udp", "icmp", "ipsec-esp", "gre", "ospf", "vrrp")

private val ipDecisions = choice("action", "allow", "deny")

private val ipHostFields = listOf(choice("method", "any", "GET", "POST", "HEAD", "CONNECT"), ipDecisions)

internal fun DemoBuilder.ipDemo() {
    ipDhcpDemo()
    ipFirewallDemo()
    ipIpsecDemo()
    ipHotspotDemo()
    ipSharingDemo()
    ipSystemDemo()
}

private fun DemoBuilder.ipDhcpDemo() {
    list("ip/dhcp-client/option") {
        row("name=clientid_duid", "code=61", "value=0xff\$(CLIENT_DUID)")
        row("name=clientid", "code=61", "value=0x01\$(CLIENT_MAC)")
        row("name=hostname", "code=12", "value=\$(HOSTNAME)")
    }
    list("ip/dhcp-server/option") {
        row("name=wpad", "code=252", "value='http://192.168.88.1/wpad.dat'")
        row("name=ntp-server", "code=42", "value=0xC0A85801")
        row("name=domain-search", "code=119", "value=0x046C616E00")
        row("name=classless-routes", "code=121", "value=0x100A14C0A858FE")
    }
    list("ip/dhcp-server/option/sets") {
        row("name=lan-extras", "options=wpad,ntp-server,domain-search")
        row("name=branch-routes", "options=classless-routes")
    }
    list("ip/dhcp-server/matcher", fields = listOf(choice("matching-type", "exact", "substring"))) {
        row("name=desk-phones", "server=defconf", "code=60", "value=SIPPHONE", "address-pool=dhcp", "option-set=lan-extras", "matching-type=substring", "disabled=false")
        row("name=guest-devices", "server=defconf", "code=60", "value=android-dhcp", "address-pool=guest", "option-set=branch-routes", "matching-type=substring", "disabled=true")
    }
    list("ip/dhcp-server/alert") {
        row("interface=bridge", "valid-server=00:00:5E:00:53:02", "alert-timeout=1h", "on-alert=:log warning \"unknown DHCP server on bridge\"", "unknown-server=", "alerting=false", "disabled=false", "comment=rogue server watch")
    }
    settings("ip/dhcp-server/config", "store-leases-disk=5m", "accounting=true", "interim-update=0s", "radius-password=mikrotik")
    list("ip/dhcp-relay") {
        row("name=office-relay", "interface=bridge", "dhcp-server=10.20.0.10", "local-address=192.168.88.1", "delay-threshold=none", "dhcp-server-vrf=main", "local-address-as-src-ip=false", "add-relay-info=false", "requests=0", "responses=0", "invalid=false", "disabled=true", "comment=standby, the local server defconf answers on bridge")
        row("name=vpn-relay", "interface=wg-home", "dhcp-server=10.20.0.10", "local-address=10.66.66.1", "delay-threshold=none", "dhcp-server-vrf=main", "local-address-as-src-ip=true", "add-relay-info=true", "relay-info-remote-id=demo-hap-vpn", "requests=214", "responses=210", "invalid=false", "disabled=false")
    }
    list("ip/pool/used", writable = false) {
        for ((host, mac) in listOf("20" to "20", "30" to "30", "101" to "65", "102" to "66", "103" to "67", "110" to "6E", "121" to "79", "130" to "82")) {
            row("pool=dhcp", "address=192.168.88.$host", "owner=00:00:5E:00:53:$mac", "info=defconf")
        }
    }
}

private fun DemoBuilder.ipFirewallDemo() {
    val mangleActions = choice("action", "accept", "change-mss", "jump", "log", "mark-connection", "mark-packet", "mark-routing", "passthrough", "return", "set-priority")
    val rawActions = choice("action", "accept", "drop", "jump", "log", "notrack", "passthrough", "return")
    list("ip/firewall/mangle", ordered = true, fields = listOf(ipChains, ipProtocols, mangleActions), live = counting()) {
        row("chain=prerouting", "action=mark-connection", "new-connection-mark=voip-conn", "passthrough=true", "protocol=udp", "dst-port=5060", "dst-address=198.51.100.0/24", "in-interface-list=LAN", "comment=SIP towards the provider", "disabled=false")
        row("chain=prerouting", "action=mark-packet", "connection-mark=voip-conn", "new-packet-mark=voip", "passthrough=false", "in-interface-list=LAN", "comment=VoIP packets", "disabled=false")
        row("chain=prerouting", "action=mark-packet", "new-packet-mark=dns-replies", "passthrough=true", "protocol=udp", "src-port=53", "in-interface-list=WAN", "comment=DNS answers from the provider", "disabled=false")
        row("chain=forward", "action=change-mss", "new-mss=clamp-to-pmtu", "passthrough=true", "protocol=tcp", "tcp-flags=syn", "out-interface=wg-home", "comment=clamp MSS for WireGuard", "disabled=false")
        row("chain=prerouting", "action=mark-routing", "new-routing-mark=guest", "passthrough=false", "src-address=192.168.89.0/24", "in-interface=bridge", "comment=guest network uses its own routing table", "disabled=false")
        row("chain=forward", "action=mark-packet", "new-packet-mark=web", "passthrough=true", "protocol=tcp", "port=80,443", "in-interface-list=LAN", "out-interface-list=WAN", "comment=count web traffic", "disabled=false")
        row("chain=forward", "action=mark-connection", "new-connection-mark=admin-wan", "passthrough=true", "src-address-list=admin", "dst-address-list=!blocked", "in-interface=bridge", "out-interface=ether1", "comment=admin sessions to the internet", "disabled=false")
    }
    list("ip/firewall/raw", ordered = true, fields = listOf(choice("chain", "prerouting", "output"), ipProtocols, rawActions), live = counting()) {
        row("chain=prerouting", "action=drop", "src-address-list=blocked", "in-interface-list=WAN", "comment=blocked hosts never reach connection tracking", "disabled=false")
        row("chain=prerouting", "action=drop", "src-address=10.0.0.0/8", "in-interface=ether1", "comment=private source addresses on the WAN", "disabled=false")
        row("chain=prerouting", "action=drop", "src-address=198.51.100.7", "dst-address=203.0.113.50", "protocol=tcp", "dst-port=22", "in-interface-list=WAN", "comment=known SSH scanner", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=tcp", "dst-port=23,2323", "in-interface-list=WAN", "comment=telnet probes", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=udp", "src-port=0", "in-interface-list=WAN", "comment=UDP source port 0", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=udp", "port=1900", "in-interface-list=WAN", "comment=SSDP from the internet", "disabled=false")
        row("chain=prerouting", "action=drop", "dst-address-list=blocked", "in-interface-list=LAN", "comment=LAN hosts may not reach blocked addresses", "disabled=false")
        row("chain=output", "action=drop", "dst-address-list=blocked", "out-interface=ether1", "comment=the router never talks to blocked addresses", "disabled=false")
        row("chain=output", "action=notrack", "protocol=udp", "dst-port=123", "out-interface-list=WAN", "comment=skip connection tracking for NTP", "disabled=false")
    }
    list("ip/firewall/service-port") {
        row("name=ftp", "ports=21", "invalid=false", "disabled=false")
        row("name=tftp", "ports=69", "invalid=false", "disabled=false")
        row("name=irc", "ports=6667", "invalid=false", "disabled=true")
        row("name=h323", "ports=1720", "invalid=false", "disabled=true")
        row("name=sip", "ports=5060,5061", "sip-direct-media=true", "sip-timeout=1h", "invalid=false", "disabled=false")
        row("name=pptp", "ports=1723", "invalid=false", "disabled=true")
        row("name=udplite", "invalid=false", "disabled=false")
        row("name=dccp", "invalid=false", "disabled=false")
        row("name=sctp", "invalid=false", "disabled=false")
    }
    list("ip/firewall/layer7-protocol") {
        row("name=http", "regexp=^.+(GET|POST|HEAD).*HTTP/1\\.[01]")
        row("name=ssh", "regexp=^ssh-[12]\\.[0-9]")
        row("name=sip", "regexp=^(invite|register|cancel|message|subscribe|notify) sip:")
    }
}

private fun DemoBuilder.ipIpsecDemo() {
    list(
        "ip/ipsec/policy",
        ordered = true,
        fields = listOf(choice("action", "none", "discard", "encrypt"), choice("level", "require", "use", "unique"), choice("protocol", "all", "tcp", "udp", "icmp")),
    ) {
        row("src-address=::/0", "src-port=any", "dst-address=::/0", "dst-port=any", "protocol=all", "action=encrypt", "level=require", "ipsec-protocols=esp", "proposal=default", "group=default", "template=true", "default=true", "dynamic=false", "invalid=false", "disabled=false")
        row("peer=branch-office", "tunnel=true", "src-address=192.168.88.0/24", "src-port=any", "dst-address=192.0.2.0/24", "dst-port=any", "protocol=all", "action=encrypt", "level=require", "ipsec-protocols=esp", "sa-src-address=203.0.113.50", "sa-dst-address=198.51.100.20", "proposal=branch-esp", "ph2-count=1", "ph2-state=established", "active=true", "template=false", "default=false", "dynamic=false", "invalid=false", "disabled=false", "comment=site-to-site to the branch office")
    }
    list("ip/ipsec/proposal") {
        row("name=default", "auth-algorithms=sha1", "enc-algorithms=aes-256-cbc,aes-192-cbc,aes-128-cbc", "lifetime=30m", "pfs-group=modp1024", "default=true", "disabled=false")
        row("name=branch-esp", "auth-algorithms=sha256", "enc-algorithms=aes-256-cbc", "lifetime=1h", "pfs-group=modp2048", "default=false", "disabled=false")
    }
    list("ip/ipsec/policy/group") {
        row("name=default", "default=true")
        row("name=branch", "default=false")
    }
    list("ip/ipsec/peer", fields = listOf(choice("exchange-mode", "main", "aggressive", "base", "ike2"))) {
        row("name=branch-office", "address=198.51.100.20/32", "local-address=203.0.113.50", "port=500", "profile=branch-ike2", "exchange-mode=ike2", "passive=false", "send-initial-contact=true", "dynamic=false", "disabled=false", "comment=branch office")
        row("name=lab-peer", "address=192.0.2.200/32", "port=500", "profile=default", "exchange-mode=ike2", "passive=true", "send-initial-contact=true", "dynamic=false", "disabled=true", "comment=lab router, switched off")
    }
    list(
        "ip/ipsec/identity",
        fields = listOf(choice("auth-method", "pre-shared-key", "pre-shared-key-xauth", "rsa-key", "digital-signature", "eap"), choice("match-by", "remote-id", "certificate")),
    ) {
        row("peer=branch-office", "auth-method=pre-shared-key", "secret=demo-only-psk", "my-id=fqdn:demo-hap.example.net", "remote-id=fqdn:branch.example.net", "match-by=remote-id", "generate-policy=no", "policy-template-group=default", "disabled=false")
        row("peer=lab-peer", "auth-method=pre-shared-key", "secret=demo-only-lab", "my-id=auto", "remote-id=ignore", "match-by=remote-id", "mode-config=rw-pool", "generate-policy=port-strict", "policy-template-group=branch", "disabled=true")
    }
    list("ip/ipsec/profile", fields = listOf(choice("proposal-check", "obey", "claim", "strict", "exact"))) {
        row("name=default", "hash-algorithm=sha1", "prf-algorithm=auto", "enc-algorithm=aes-128,3des", "dh-group=modp2048,modp1024", "lifetime=1d", "lifebytes=0", "proposal-check=obey", "nat-traversal=true", "ppk=no", "dpd-interval=2m", "dpd-maximum-failures=5", "default=true")
        row("name=branch-ike2", "hash-algorithm=sha256", "prf-algorithm=auto", "enc-algorithm=aes-256", "dh-group=ecp256,modp2048", "lifetime=8h", "lifebytes=0", "proposal-check=obey", "nat-traversal=true", "ppk=no", "dpd-interval=30s", "dpd-maximum-failures=5", "default=false")
    }
    list(
        "ip/ipsec/active-peers",
        writable = false,
        live = { _, tick ->
            mapOf(
                "uptime" to duration(11_390L + tick), "last-seen" to "${tick % 8}s", "rx-bytes" to "${482_211_904L + tick * 3_100L}", "tx-bytes" to "${61_902_117L + tick * 1_900L}",
                "rx-packets" to "${611_204L + tick * 3}", "tx-packets" to "${402_118L + tick * 2}",
            )
        },
    ) {
        row("remote-address=198.51.100.20", "local-address=203.0.113.50", "state=established", "side=initiator", "uptime=3h9m50s", "last-seen=4s", "ph2-total=1", "spii=0x7d3a90c1e5b24f68", "spir=0xa1f405d97c3e2b80", "rx-packets=611204", "rx-bytes=482211904", "tx-packets=402118", "tx-bytes=61902117", "responder=false", "natt-peer=false", "dynamic=false")
    }
    list("ip/ipsec/mode-config") {
        row("name=request-only", "responder=false", "use-responder-dns=true", "default=true")
        row("name=rw-pool", "responder=true", "address-pool=guest", "address-prefix-length=32", "split-include=192.168.88.0/24", "split-dns=lan", "system-dns=true", "static-dns=192.168.88.1", "default=false")
    }
    list("ip/ipsec/installed-sa", writable = false) {
        row("spi=0xC4A37E21", "src-address=198.51.100.20", "dst-address=203.0.113.50", "state=mature", "auth-algorithm=sha256", "enc-algorithm=aes-cbc", "enc-key-size=256", "addtime=2026-10-31 09:29:57", "expires-in=14m52s", "add-lifetime=24m/30m", "current-bytes=18402211", "current-packets=24310", "replay=128", "seen-traffic=true", "hw-aead=false")
        row("spi=0x0B6E52D9", "src-address=203.0.113.50", "dst-address=198.51.100.20", "state=mature", "auth-algorithm=sha256", "enc-algorithm=aes-cbc", "enc-key-size=256", "addtime=2026-10-31 09:29:57", "expires-in=14m52s", "add-lifetime=24m/30m", "current-bytes=2911840", "current-packets=19844", "replay=128", "seen-traffic=true", "hw-aead=false")
    }
    settings(
        "ip/ipsec/statistics", "in-errors=0", "in-buffer-errors=0", "in-header-errors=0", "in-no-states=3", "in-state-protocol-errors=0", "in-state-mode-errors=0",
        "in-state-sequence-errors=0", "in-state-expired=0", "in-state-mismatches=0", "in-state-invalid=0", "in-template-mismatches=0", "in-no-policies=12",
        "in-policy-blocked=0", "in-policy-errors=0", "out-errors=0", "out-bundle-errors=0", "out-bundle-check-errors=0", "out-no-states=0", "out-state-protocol-errors=0",
        "out-state-mode-errors=0", "out-state-sequence-errors=0", "out-state-expired=0", "out-policy-blocked=0", "out-policy-dead=0", "out-policy-errors=0",
        writable = false,
    )
    settings("ip/ipsec/settings", "accounting=true", "interim-update=0s", "xauth-use-radius=false")
}

private fun DemoBuilder.ipHotspotDemo() {
    list("ip/hotspot") {
        row("name=hotspot1", "interface=bridge", "address-pool=dhcp", "profile=hsprof1", "idle-timeout=5m", "keepalive-timeout=none", "login-timeout=none", "addresses-per-mac=2", "HTTPS=false", "invalid=false", "disabled=false")
    }
    list("ip/hotspot/profile") {
        row("name=default", "hotspot-address=0.0.0.0", "html-directory=hotspot", "http-proxy=0.0.0.0:0", "smtp-server=0.0.0.0", "login-by=cookie,http-chap", "http-cookie-lifetime=3d", "ssl-certificate=none", "split-user-domain=false", "use-radius=false", "install-hotspot-queue=false", "default=true")
        row("name=hsprof1", "hotspot-address=192.168.88.1", "dns-name=wifi.example.net", "html-directory=hotspot", "http-proxy=0.0.0.0:0", "smtp-server=0.0.0.0", "rate-limit=5M/20M", "login-by=cookie,http-chap", "mac-auth-mode=mac-as-username", "http-cookie-lifetime=1d", "ssl-certificate=none", "split-user-domain=false", "trial-uptime-limit=30m", "trial-uptime-reset=1d", "trial-user-profile=default", "use-radius=false", "radius-accounting=true", "radius-interim-update=received", "radius-mac-format=XX:XX:XX:XX:XX:XX", "nas-port-type=wireless-802.11", "install-hotspot-queue=true", "default=false")
    }
    list(
        "ip/hotspot/user",
        live = { row, tick ->
            if (row["name"] == "guest-01") mapOf("uptime" to duration(8_045L + tick), "bytes-in" to "${48_211_904L + tick * 6_400L}", "bytes-out" to "${6_120_311L + tick * 900L}") else emptyMap()
        },
    ) {
        row("server=all", "name=anna", "password=demo-pass-anna", "mac-address=00:00:5E:00:53:66", "profile=staff", "email=anna@example.net", "uptime=5d3h12m", "bytes-in=2104339811", "packets-in=1840211", "bytes-out=311220881", "packets-out=902144", "limit-uptime=0s", "limit-bytes-in=0", "limit-bytes-out=0", "limit-bytes-total=0", "dynamic=false", "default=false", "disabled=false")
        row("server=hotspot1", "name=guest-01", "password=demo-pass-g01", "profile=day-pass", "uptime=2h14m5s", "bytes-in=48211904", "packets-in=61230", "bytes-out=6120311", "packets-out=42110", "limit-uptime=1d", "limit-bytes-in=0", "limit-bytes-out=0", "limit-bytes-total=2147483648", "dynamic=false", "default=false", "disabled=false")
        row("server=hotspot1", "name=voucher-7f3k", "password=7f3k", "profile=day-pass", "uptime=0s", "bytes-in=0", "packets-in=0", "bytes-out=0", "packets-out=0", "limit-uptime=1d", "limit-bytes-total=2147483648", "dynamic=false", "default=false", "disabled=false", "comment=unused voucher")
        row("server=all", "name=reception", "password=demo-pass-desk", "address=192.168.88.50", "mac-address=00:00:5E:00:53:50", "profile=staff", "routes=10.20.0.0/16", "uptime=11d6h40m", "bytes-in=8812340211", "packets-in=7120311", "bytes-out=611902331", "packets-out=3011840", "limit-uptime=0s", "dynamic=false", "default=false", "disabled=false")
    }
    list("ip/hotspot/user/profile") {
        row("name=default", "shared-users=1", "session-timeout=none", "idle-timeout=none", "keepalive-timeout=2m", "status-autorefresh=1m", "add-mac-cookie=true", "mac-cookie-timeout=3d", "transparent-proxy=true", "open-status-page=always", "advertise=false", "advertise-url=http://wifi.example.net/ad.html", "advertise-interval=30m,10m", "advertise-timeout=1m", "queue-type=default-small", "parent-queue=none", "insert-queue-before=first", "default=true")
        row("name=day-pass", "shared-users=1", "rate-limit=2M/10M", "session-timeout=1d", "idle-timeout=15m", "keepalive-timeout=2m", "status-autorefresh=1m", "address-pool=dhcp", "incoming-packet-mark=hs-up", "outgoing-packet-mark=hs-down", "add-mac-cookie=true", "mac-cookie-timeout=1d", "transparent-proxy=false", "open-status-page=always", "advertise=false", "advertise-interval=30m,10m", "advertise-timeout=1m", "on-login=:log info \"hotspot login\"", "on-logout=:log info \"hotspot logout\"", "queue-type=default-small", "parent-queue=none", "insert-queue-before=first", "default=false")
        row("name=staff", "shared-users=3", "rate-limit=20M/50M", "session-timeout=none", "idle-timeout=none", "keepalive-timeout=2m", "status-autorefresh=1m", "address-pool=dhcp", "add-mac-cookie=true", "mac-cookie-timeout=7d", "transparent-proxy=true", "open-status-page=http-login", "advertise=false", "queue-type=default-small", "parent-queue=none", "insert-queue-before=first", "default=false")
    }
    list("ip/hotspot/active", writable = false) {
        row("server=hotspot1", "user=anna", "address=192.168.88.102", "mac-address=00:00:5E:00:53:66", "login-by=cookie", "uptime=1h41m22s", "session-time-left=none", "idle-time=12s", "idle-timeout=none", "keepalive-timeout=2m", "bytes-in=88120344", "bytes-out=9120113", "packets-in=91244", "packets-out=60211", "radius=false", "blocked=false")
        row("server=hotspot1", "user=guest-01", "address=192.168.88.103", "mac-address=00:00:5E:00:53:67", "login-by=http-chap", "uptime=2h14m5s", "session-time-left=21h45m55s", "idle-time=3s", "idle-timeout=15m", "keepalive-timeout=2m", "bytes-in=48211904", "bytes-out=6120311", "packets-in=61230", "packets-out=42110", "radius=false", "blocked=false")
    }
    list("ip/hotspot/host", writable = false) {
        row("mac-address=00:00:5E:00:53:65", "address=192.168.88.101", "to-address=192.168.88.101", "server=hotspot1", "uptime=3d4h6m", "idle-time=1s", "idle-timeout=none", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.101:68->255.255.255.255:67,DHCP", "bytes-in=611220881", "bytes-out=88211904", "packets-in=702114", "packets-out=401230", "authorized=false", "bypassed=true", "dynamic=true", "blocked=false", "DHCP=true")
        row("mac-address=00:00:5E:00:53:66", "address=192.168.88.102", "to-address=192.168.88.102", "server=hotspot1", "uptime=1h41m22s", "idle-time=12s", "idle-timeout=none", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.102:68->255.255.255.255:67,DHCP", "bytes-in=88120344", "bytes-out=9120113", "packets-in=91244", "packets-out=60211", "authorized=true", "bypassed=false", "dynamic=true", "blocked=false", "DHCP=true")
        row("mac-address=00:00:5E:00:53:67", "address=192.168.88.103", "to-address=192.168.88.103", "server=hotspot1", "uptime=2h14m5s", "idle-time=3s", "idle-timeout=15m", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.103:68->255.255.255.255:67,DHCP", "bytes-in=48211904", "bytes-out=6120311", "packets-in=61230", "packets-out=42110", "authorized=true", "bypassed=false", "dynamic=true", "blocked=false", "DHCP=true")
        row("mac-address=00:00:5E:00:53:6E", "address=192.168.88.110", "to-address=192.168.88.110", "server=hotspot1", "uptime=3d4h1m", "idle-time=0s", "idle-timeout=none", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.110:68->255.255.255.255:67,DHCP", "bytes-in=512004112", "bytes-out=5822101", "packets-in=391082", "packets-out=88123", "authorized=false", "bypassed=true", "dynamic=true", "blocked=false", "DHCP=true")
        row("mac-address=00:00:5E:00:53:79", "address=192.168.88.121", "to-address=192.168.88.121", "server=hotspot1", "uptime=3d3h58m", "idle-time=4m27s", "idle-timeout=none", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.121:68->255.255.255.255:67,DHCP", "bytes-in=2811904", "bytes-out=611220", "packets-in=7212", "packets-out=7210", "authorized=false", "bypassed=true", "dynamic=true", "blocked=false", "DHCP=true")
        row("mac-address=00:00:5E:00:53:82", "address=192.168.88.130", "to-address=192.168.88.130", "server=hotspot1", "uptime=14m9s", "idle-time=2s", "idle-timeout=5m", "keepalive-timeout=2m", "host-dead-time=1m", "found-by=192.168.88.130:68->255.255.255.255:67,DHCP", "bytes-in=29011888", "bytes-out=1893211", "packets-in=24012", "packets-out=18821", "authorized=false", "bypassed=false", "dynamic=true", "blocked=false", "DHCP=true")
    }
    list("ip/hotspot/ip-binding", ordered = true, fields = listOf(choice("type", "regular", "bypassed", "blocked"))) {
        row("mac-address=00:00:5E:00:53:20", "address=192.168.88.20", "server=hotspot1", "type=bypassed", "disabled=false", "comment=NAS")
        row("mac-address=00:00:5E:00:53:30", "address=192.168.88.30", "server=hotspot1", "type=bypassed", "disabled=false", "comment=printer")
        row("mac-address=00:00:5E:00:53:65", "address=192.168.88.101", "server=hotspot1", "type=bypassed", "disabled=false", "comment=admin laptop")
        row("mac-address=00:00:5E:00:53:6E", "address=192.168.88.110", "server=hotspot1", "type=bypassed", "disabled=false", "comment=TV")
        row("mac-address=00:00:5E:00:53:79", "address=192.168.88.121", "server=hotspot1", "type=bypassed", "disabled=false", "comment=sensor")
        row("mac-address=00:00:5E:00:53:82", "address=192.168.88.130", "to-address=192.168.88.130", "server=hotspot1", "type=regular", "disabled=false", "comment=game console, login required")
        row("mac-address=00:00:5E:00:53:99", "server=all", "type=blocked", "disabled=false", "comment=lost phone")
    }
    list("ip/hotspot/service-port") {
        row("name=ftp", "ports=21", "disabled=false")
    }
    list("ip/hotspot/walled-garden", ordered = true, fields = ipHostFields) {
        row("action=allow", "server=hotspot1", "method=GET", "dst-host=*.updates.example.org", "dst-port=80", "path=/packages/*", "hits=1432", "dynamic=false", "disabled=false", "comment=OS updates before login")
        row("action=allow", "server=hotspot1", "method=any", "dst-host=login.example.net", "dst-port=443", "hits=311", "dynamic=false", "disabled=false")
        row("action=allow", "src-address=192.168.88.20", "method=any", "dst-host=*", "hits=88", "dynamic=false", "disabled=false", "comment=NAS")
        row("action=deny", "server=hotspot1", "method=any", "dst-host=ads.example.net", "hits=8", "dynamic=false", "disabled=false")
    }
    list("ip/hotspot/walled-garden/ip", ordered = true, fields = listOf(choice("action", "accept", "drop", "reject"), ipProtocols)) {
        row("action=accept", "server=hotspot1", "dst-address=203.0.113.2", "protocol=udp", "dst-port=53", "dynamic=false", "disabled=false", "comment=provider DNS")
        row("action=accept", "server=hotspot1", "dst-host=time.example.net", "protocol=udp", "dst-port=123", "dynamic=false", "disabled=false")
        row("action=accept", "src-address=192.168.88.20", "dst-address=0.0.0.0/0", "dynamic=false", "disabled=false", "comment=NAS")
    }
    list("ip/hotspot/cookie", writable = false) {
        row("user=anna", "mac-address=00:00:5E:00:53:66", "expires-in=2d19h38m", "mac-cookie=4be0a7d213c9f580")
        row("user=guest-01", "mac-address=00:00:5E:00:53:67", "expires-in=21h45m55s", "mac-cookie=91d3f6c0a2e57b48")
    }
}

private fun DemoBuilder.ipSharingDemo() {
    settings(
        "ip/cloud", "ddns-enabled=true", "ddns-update-interval=none", "update-time=true", "public-address=203.0.113.50", "public-address-ipv6=2001:db8:ff01::5",
        "dns-name=a1b2c3d4e5f6.sn.mynetname.net", "status=updated",
    )
    settings("ip/cloud/advanced", "use-local-address=false")
    list("ip/kid-control") {
        row("name=kids", "sun=8h-21h", "mon=7h30m-21h", "tue=7h30m-21h", "wed=7h30m-21h", "thu=7h30m-21h", "fri=7h30m-22h", "sat=8h-23h", "rate-limit=5M/20M", "tur-sun=0s-1d", "tur-mon=0s-0s", "tur-tue=0s-0s", "tur-wed=0s-0s", "tur-thu=0s-0s", "tur-fri=0s-0s", "tur-sat=0s-1d", "paused=false")
        row("name=guests", "sun=0s-1d", "mon=0s-1d", "tue=0s-1d", "wed=0s-1d", "thu=0s-1d", "fri=0s-1d", "sat=0s-1d", "rate-limit=2M/2M", "tur-sun=0s-0s", "tur-mon=0s-0s", "tur-tue=0s-0s", "tur-wed=0s-0s", "tur-thu=0s-0s", "tur-fri=0s-0s", "tur-sat=0s-0s", "paused=true")
    }
    list(
        "ip/kid-control/device",
        live = { row, tick ->
            val salt = row.id.hashCode()
            mapOf("rate-up" to "${wander(180_000, tick, salt)}", "rate-down" to "${wander(2_400_000, tick, salt + 1)}")
        },
    ) {
        row("name=galaxy-s25", "mac-address=00:00:5E:00:53:67", "user=kids", "ip-address=192.168.88.103", "rate-up=182400", "rate-down=2411000", "bytes-up=611220881", "bytes-down=9120344112", "activity=web", "dynamic=false", "blocked=false", "limited=false", "inactive=false")
        row("name=ps5", "mac-address=00:00:5E:00:53:82", "user=kids", "ip-address=192.168.88.130", "rate-up=410000", "rate-down=3640000", "bytes-up=29011888", "bytes-down=1893211004", "activity=game", "dynamic=false", "blocked=false", "limited=true", "inactive=false")
        row("name=tv-livingroom", "mac-address=00:00:5E:00:53:6E", "user=guests", "ip-address=192.168.88.110", "rate-up=22000", "rate-down=3600000", "bytes-up=5822101", "bytes-down=512004112", "activity=video", "dynamic=false", "blocked=false", "limited=false", "inactive=false")
    }
    list("ip/media") {
        row("interface=bridge", "path=pub/media", "friendly-name=Office media", "allowed-ip=192.168.88.0/24", "allowed-hostname=nas.lan", "status=running", "disabled=false")
        row("interface=wg-home", "path=pub/photos", "friendly-name=Photos", "allowed-ip=10.66.66.0/24", "status=running", "disabled=false")
    }
    settings("ip/media/settings", "thumbnails=media-thumbs")
    settings("ip/nat-pmp", "enabled=false")
    list("ip/nat-pmp/interfaces", fields = listOf(choice("type", "internal", "external"))) {
        row("interface=ether1", "type=external", "forced-ip=203.0.113.50", "disabled=false")
        row("interface=bridge", "type=internal", "disabled=false")
    }
    settings("ip/upnp", "enabled=false", "allow-disable-external-interface=false", "show-dummy-rule=true")
    list("ip/upnp/interfaces", fields = listOf(choice("type", "internal", "external"))) {
        row("interface=ether1", "type=external", "forced-ip=203.0.113.50", "disabled=false")
        row("interface=bridge", "type=internal", "disabled=false")
    }
    list("ip/reverse-proxy") {
        row("sni=nas.example.net", "ip-address=192.168.88.20", "port=443", "certificate=router.lan", "vrf=main", "disabled=false")
        row("sni=files.example.net", "ip-address=192.168.88.20", "port=8080", "certificate=router.lan", "vrf=main", "disabled=false")
    }
    settings("ip/smb", "enabled=true", "domain=OFFICE", "interfaces=bridge", "allow-guests=false", "status=running", "comment=demo-hap")
    list("ip/smb/shares") {
        row("name=pub", "directory=/pub", "read-only=false", "require-encryption=false", "valid-users=anna,guest", "invalid-users=", "disabled=false", "comment=default share")
        row("name=docs", "directory=/usb1/docs", "read-only=true", "require-encryption=true", "valid-users=anna", "invalid-users=guest", "disabled=false")
    }
    list("ip/smb/users") {
        row("name=guest", "password=", "read-only=true", "disabled=false", "comment=default guest")
        row("name=anna", "password=demo-pass-anna", "read-only=false", "disabled=false")
    }
    settings(
        "ip/socks", "enabled=true", "port=1080", "connection-idle-timeout=2m", "max-connections=200", "vrf=main", "version=5", "auth-method=password",
        fields = listOf(choice("auth-method", "none", "password"), choice("version", "4", "5")),
    )
    list("ip/socks/access", ordered = true, fields = listOf(ipDecisions)) {
        row("src-address=192.168.88.0/24", "action=allow", "disabled=false", "comment=LAN")
        row("dst-address=198.51.100.0/24", "dst-port=25", "action=deny", "disabled=false", "comment=no mail relaying")
        row("src-port=0-1023", "action=deny", "disabled=false")
    }
    list("ip/socks/connections", writable = false) {
        row("src-address=192.168.88.101:50123", "dst-address=198.51.100.140:443", "tx=481920", "rx=9120344")
        row("src-address=192.168.88.20:44018", "dst-address=203.0.113.77:22", "tx=18211", "rx=40233")
    }
    list("ip/socks/users") {
        row("name=proxy-user", "password=demo-pass-socks", "only-one=true", "rate-limit=10M/10M", "disabled=false")
        row("name=backup", "password=demo-pass-backup", "only-one=false", "disabled=false")
    }
    list("ip/socksify") {
        row("name=nas-tunnel", "connection-timeout=10s", "port=1090", "socks5-server=192.168.88.20", "socks5-port=1080", "socks5-user=proxy-user", "socks5-password=demo-pass-socks", "disabled=false")
    }
}

private fun DemoBuilder.ipSystemDemo() {
    settings(
        "ip/ssh", "forwarding-enabled=no", "password-authentication=yes", "always-allow-password-login=false", "publickey-authentication-options=none", "strong-crypto=true",
        "allow-none-crypto=false", "ciphers=auto", "host-key-type=ed25519", "host-key-size=2048", "host-key-fingerprint=SHA256:Xx9w8c1oN0bV3dQe5rTyUiOpAsDfGhJkLzXcVbNmQw4",
        "known-hosts-validation=false", "known-hosts-trusted-subnets=192.168.88.0/24",
        fields = listOf(choice("forwarding-enabled", "no", "local", "remote", "both"), choice("password-authentication", "yes", "no", "yes-if-no-key")),
    )
    list("ip/ssh/known-hosts") {
        row("address=192.168.88.2", "key-type=ssh-ed25519", "string-key=AAAAC3NzaC1lZDI1NTE5AAAAIDemo")
        row("address=198.51.100.20", "key-type=ssh-rsa", "string-key=AAAAB3NzaC1yc2EAAAADAQABAAABAQDemo")
    }
    list("ip/tftp", ordered = true) {
        row("ip-addresses=192.168.88.0/24", "req-filename=.*\\.cfg", "real-filename=", "allow=true", "read-only=true", "allow-overwrite=false", "allow-rollover=false", "reading-window-size=1", "hits=12", "disabled=false")
        row("ip-addresses=10.66.66.0/24", "req-filename=firmware/.*", "real-filename=disk1/firmware", "allow=true", "read-only=true", "allow-overwrite=false", "allow-rollover=true", "reading-window-size=4", "hits=3", "disabled=false")
        row("req-filename=.*", "real-filename=", "allow=false", "read-only=true", "allow-overwrite=false", "allow-rollover=false", "reading-window-size=1", "hits=0", "disabled=false")
    }
    settings("ip/tftp/settings", "max-block-size=4096")
    settings(
        "ip/traffic-flow", "enabled=true", "interfaces=ether1", "cache-entries=32k", "active-flow-timeout=30m", "inactive-flow-timeout=15s", "packet-sampling=false",
        "sampling-interval=1", "sampling-space=0",
    )
    list("ip/traffic-flow/target") {
        row("dst-address=192.168.88.20", "port=2055", "version=9", "src-address=0.0.0.0", "v9-template-refresh=20", "v9-template-timeout=30m", "disabled=false")
        row("dst-address=10.20.0.10", "port=4739", "version=ipfix", "src-address=192.168.88.1", "disabled=false")
    }
    settings(
        "ip/traffic-flow/ipfix", "last-forwarded=true", "first-forwarded=true", "sys-init-time=true", "packets=true", "bytes=true", "in-interface=true", "out-interface=true",
        "src-address=true", "dst-address=true", "src-port=true", "dst-port=true", "protocol=true", "tos=true", "gateway=true", "src-address-mask=true", "dst-address-mask=true",
        "tcp-flags=true", "dst-mac-address=true", "src-mac-address=true", "nat-src-address=true", "nat-dst-address=true", "nat-src-address6=false", "nat-dst-address6=false",
        "nat-src-port=true", "nat-dst-port=true", "ipv6-flow-label=false", "ttl=true", "is-multicast=false", "ip-header-length=false", "ip-total-length=false",
        "udp-length=false", "tcp-seq-num=false", "tcp-ack-num=false", "tcp-window-size=false", "igmp-type=false", "icmp-type=false", "icmp-code=false", "nat-events=false",
    )
    list("ip/vrf") {
        row("name=lab", "interfaces=lo", "invalid=false", "comment=reserved for tests")
    }
    list("ip/packing") {
        row("interface=wg-home", "packing=simple", "unpacking=simple", "aggregated-size=1420", "disabled=false")
    }
    settings(
        "ip/proxy", "enabled=true", "src-address=0.0.0.0", "port=8080", "anonymous=false", "parent-proxy=::", "parent-proxy-port=0", "cache-administrator=webmaster",
        "max-cache-size=unlimited", "max-cache-object-size=2048KiB", "cache-on-disk=false", "max-client-connections=600", "max-server-connections=600", "max-fresh-time=3d",
        "serialize-connections=false", "always-from-cache=false", "cache-hit-dscp=4", "cache-path=web-proxy", "status=running", "uptime=3d4h9m1s", "client-connections=14",
        "server-connections=9", "requests=18420", "hits=4210", "cache-used=51200", "total-ram-used=7424", "received-from-servers=612044", "sent-to-clients=701230",
        "hits-sent-to-clients=88421",
        live = { _, tick ->
            mapOf(
                "uptime" to duration(274_141L + tick), "client-connections" to "${wander(14, tick, 2)}", "server-connections" to "${wander(9, tick, 4)}",
                "requests" to "${18_420L + tick * 3}", "hits" to "${4_210L + tick}", "received-from-servers" to "${612_044L + tick * 12}", "sent-to-clients" to "${701_230L + tick * 14}",
            )
        },
    )
    list("ip/proxy/access", ordered = true, fields = ipHostFields) {
        row("action=deny", "dst-host=*.ads.example.net", "hits=212", "disabled=false", "comment=ad servers")
        row("action=deny", "method=GET", "path=*.exe", "hits=17", "disabled=false", "comment=binary downloads")
        row("action=allow", "src-address=192.168.88.0/24", "dst-port=80,443,8080", "local-port=8080", "hits=18400", "disabled=false")
        row("action=deny", "redirect-to=wifi.example.net/blocked.html", "hits=3", "disabled=false", "comment=everything else")
    }
    list("ip/proxy/cache", ordered = true, fields = ipHostFields) {
        row("action=deny", "dst-host=video.example.net", "hits=4", "disabled=false", "comment=streams are not cached")
        row("action=deny", "path=*.php", "hits=88", "disabled=false")
        row("action=allow", "hits=4210", "disabled=false")
    }
    list("ip/proxy/direct", ordered = true, fields = ipHostFields) {
        row("action=allow", "dst-address=192.168.88.0/24", "hits=312", "disabled=false", "comment=LAN servers are reached directly")
        row("action=deny", "hits=0", "disabled=false")
    }
    list("ip/proxy/connections", writable = false) {
        row("client=192.168.88.101:51944", "server=198.51.100.140:80", "protocol=http", "state=established", "rx-bytes=481920", "tx-bytes=9120")
        row("client=192.168.88.102:60112", "server=198.51.100.141:80", "protocol=http", "state=established", "rx-bytes=1204330", "tx-bytes=22110")
    }
    list("ip/proxy/cache-contents", writable = false) {
        row("url=http://198.51.100.140/pub/index.html", "status=cached", "size=18211", "last-access=2026-10-31 09:40:12")
        row("url=http://198.51.100.140/pub/logo.png", "status=cached", "size=41230", "last-access=2026-10-31 09:40:12")
        row("url=http://198.51.100.141/updates/list.txt", "status=stale", "size=2211", "last-access=2026-10-30 22:14:51")
    }
    list("ip/proxy/lookups", writable = false) {
        row("url=http://198.51.100.140/pub/index.html", "count=212")
        row("url=http://198.51.100.141/updates/list.txt", "count=18")
    }
    list("ip/proxy/inserts", writable = false) {
        row("url=http://198.51.100.140/pub/logo.png", "count=1")
        row("url=http://198.51.100.141/updates/list.txt", "count=3")
    }
    list("ip/proxy/refreshes", writable = false) {
        row("url=http://198.51.100.141/updates/list.txt", "count=2")
    }
    settings(
        "snmp", "enabled=true", "contact=admin@example.net", "location=server closet", "engine-id=0x80003a8c04a1b2c3d4", "trap-target=192.168.88.20", "trap-community=public",
        "trap-version=2", "trap-generators=temp-exception", "trap-interfaces=all", "src-address=::", "vrf=main",
        fields = listOf(choice("trap-version", "1", "2", "3")),
    )
    list("snmp/community", fields = listOf(choice("security", "none", "authorized", "private"), choice("authentication-protocol", "MD5", "SHA1", "SHA256"), choice("encryption-protocol", "DES", "AES"))) {
        row("name=public", "addresses=::/0", "security=none", "read-access=true", "write-access=false", "default=true")
        row("name=office-v3", "addresses=192.168.88.0/24", "security=private", "read-access=true", "write-access=false", "authentication-protocol=SHA1", "encryption-protocol=AES", "authentication-password=demo-auth-pass", "encryption-password=demo-priv-pass", "default=false")
        row("name=monitoring", "addresses=192.168.88.20/32,10.66.66.0/24", "security=authorized", "read-access=true", "write-access=false", "denied-oids=1.3.6.1.4.1", "authentication-protocol=SHA256", "authentication-password=demo-auth-mon", "default=false")
    }
    settings(
        "ip/settings", "ip-forward=true", "send-redirects=true", "accept-redirects=false", "secure-redirects=true", "accept-source-route=false", "allow-fast-path=true",
        "rp-filter=no", "ipv4-multipath-hash-policy=l3", "tcp-syncookies=false", "tcp-timestamps=random-offset", "max-neighbor-entries=16384", "arp-timeout=30s",
        "icmp-rate-limit=10", "icmp-rate-mask=0x1818", "icmp-errors-use-inbound-interface-address=false", "ipv4-high-fragment-thresh=4194304", "ipv4-fragment-time=30s",
        "ipv4-fast-path-active=true", "ipv4-fast-path-packets=4218330", "ipv4-fast-path-bytes=3112904118", "ipv4-fasttrack-active=true", "ipv4-fasttrack-packets=88221904",
        "ipv4-fasttrack-bytes=97302114882",
        fields = listOf(choice("rp-filter", "no", "loose", "strict"), choice("ipv4-multipath-hash-policy", "l3", "l3-inner", "l4"), choice("tcp-timestamps", "disabled", "enabled", "random-offset")),
        live = { _, tick ->
            mapOf(
                "ipv4-fast-path-packets" to "${4_218_330L + tick * 91L}", "ipv4-fast-path-bytes" to "${3_112_904_118L + tick * 82_000L}",
                "ipv4-fasttrack-packets" to "${88_221_904L + tick * 340L}", "ipv4-fasttrack-bytes" to "${97_302_114_882L + tick * 380_000L}",
            )
        },
    )
    settings(
        "ip/neighbor/discovery-settings", "discover-interface-list=LAN", "discover-interval=30s", "mode=tx-and-rx", "protocol=cdp,lldp,mndp", "lldp-med-net-policy-vlan=disabled",
        "lldp-vlan-info=false", "lldp-max-frame-size=false", "lldp-mac-phy-config=false", "lldp-med=false", "dying-gasp=false", "add-dns-entries=false",
        fields = listOf(choice("mode", "tx-and-rx", "tx-only", "rx-only")),
    )
    list("ip/dns/adlist") {
        row("url=https://lists.example.com/ads.txt", "ssl-verify=true", "match-count=124", "name-count=84120", "disabled=false")
        row("file=adblock.txt", "ssl-verify=false", "match-count=18", "name-count=1204", "disabled=false")
    }
    list("ip/dns/forwarders") {
        row("name=public", "dns-servers=1.1.1.1,9.9.9.9", "doh-servers=https://dns.example.net/dns-query", "verify-doh-cert=true", "disabled=false")
        row("name=office-dns", "dns-servers=10.20.0.10", "verify-doh-cert=false", "disabled=false")
    }
}
