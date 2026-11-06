package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.duration

private val ipv6Chains = choice("chain", "prerouting", "input", "forward", "output", "postrouting")

private val ipv6Protocols = choice("protocol", "tcp", "udp", "icmpv6", "ipsec-esp", "ipsec-ah", "gre", "139")

internal fun DemoBuilder.ipv6Demo() {
    ipv6AddressingDemo()
    ipv6DhcpDemo()
    ipv6FirewallDemo()
    ipv6DiscoveryDemo()
}

private fun DemoBuilder.ipv6AddressingDemo() {
    list("ipv6/address", fields = listOf(choice("from-pool-policy", "recommended", "strict", "without-acquire"))) {
        row("address=2001:db8:50::1/64", "from-pool=isp-pd", "from-pool-policy=recommended", "interface=bridge", "actual-interface=bridge", "vrf=main", "eui-64=false", "advertise=true", "no-dad=false", "auto-link-local=true", "valid=23h48m51s", "preferred=11h48m51s", "global=true", "dynamic=false", "invalid=false", "disabled=false", "comment=LAN prefix from the provider")
        row("address=fd66:66:66::1/64", "interface=wg-home", "actual-interface=wg-home", "vrf=main", "eui-64=false", "advertise=false", "no-dad=false", "auto-link-local=true", "global=false", "dynamic=false", "invalid=false", "disabled=false", "comment=road warriors")
        row("address=2001:db8:ff01::5/128", "interface=ether1", "actual-interface=ether1", "vrf=main", "eui-64=false", "advertise=false", "no-dad=true", "valid=23h51m22s", "preferred=11h51m22s", "global=true", "dynamic=true", "invalid=false", "disabled=false")
        row("address=fe80::200:5eff:fe00:5301/64", "interface=ether1", "actual-interface=ether1", "vrf=main", "advertise=false", "link-local=true", "dynamic=true", "invalid=false", "disabled=false")
        row("address=fe80::200:5eff:fe00:5302/64", "interface=bridge", "actual-interface=bridge", "vrf=main", "advertise=false", "link-local=true", "dynamic=true", "invalid=false", "disabled=false")
        row("address=fe80::b4c2:a7ff:fe19:6d33/64", "interface=wg-home", "actual-interface=wg-home", "vrf=main", "advertise=false", "link-local=true", "dynamic=true", "invalid=false", "disabled=false")
    }
    list("ipv6/pool") {
        row("name=isp-pd", "prefix=2001:db8:50::/48", "prefix-length=64", "actual-prefix=2001:db8:50::/48", "valid-lifetime=23h51m22s", "preferred-lifetime=11h51m22s", "dynamic=true")
        row("name=lan-pd", "prefix=2001:db8:50:f00::/56", "prefix-length=60", "actual-prefix=2001:db8:50:f00::/56", "valid-lifetime=4w2d", "preferred-lifetime=1w", "dynamic=false", "comment=prefixes for downstream routers")
    }
    list("ipv6/pool/used", writable = false) {
        row("pool=isp-pd", "prefix=2001:db8:50::/64", "owner=bridge", "info=ipv6 address")
        row("pool=lan-pd", "prefix=2001:db8:50:f00::/60", "owner=0x0003000100005e0053a2", "info=lan6")
    }
    settings(
        "ipv6/settings", "disable-ipv6=false", "forward=true", "allow-fast-path=true", "accept-redirects=yes-if-forwarding-disabled", "accept-router-advertisements=yes-if-forwarding-disabled",
        "accept-router-advertisements-on=all", "disable-link-local-address=false", "stale-neighbor-detect-interval=30", "stale-neighbor-timeout=60", "min-neighbor-entries=4096",
        "soft-max-neighbor-entries=8191", "max-neighbor-entries=8192", "multipath-hash-policy=l3", "ipv6-fast-path-active=true", "ipv6-fast-path-packets=1204118",
        "ipv6-fast-path-bytes=988211904", "ipv6-fasttrack-active=true", "ipv6-fasttrack-packets=9120344", "ipv6-fasttrack-bytes=8812340211",
        fields = listOf(choice("accept-redirects", "yes-if-forwarding-disabled", "yes", "no"), choice("accept-router-advertisements", "yes-if-forwarding-disabled", "yes", "no"), choice("multipath-hash-policy", "l3", "l3-inner", "l4")),
        live = { _, tick ->
            mapOf(
                "ipv6-fast-path-packets" to "${1_204_118L + tick * 14L}", "ipv6-fast-path-bytes" to "${988_211_904L + tick * 11_000L}",
                "ipv6-fasttrack-packets" to "${9_120_344L + tick * 61L}", "ipv6-fasttrack-bytes" to "${8_812_340_211L + tick * 70_000L}",
            )
        },
    )
}

private fun DemoBuilder.ipv6DhcpDemo() {
    list(
        "ipv6/dhcp-client",
        live = { _, tick ->
            val left = duration(85_882L - tick)
            mapOf("expires-after" to left, "prefix-expires-after" to left, "address-expires-after" to left)
        },
    ) {
        row("interface=ether1", "request=address,prefix", "pool-name=isp-pd", "pool-prefix-length=64", "prefix-hint=::/0", "accept-prefix-without-address=true", "use-peer-dns=true", "use-interface-duid=false", "rapid-commit=true", "add-default-route=false", "allow-reconfigure=false", "validate-server-duid=false", "default-route-distance=1", "default-route-tables=default", "status=bound", "prefix=2001:db8:50::/48", "prefix-expires-after=23h51m22s", "address=2001:db8:ff01::5/128", "address-expires-after=23h51m22s", "expires-after=23h51m22s", "dhcp-server-v6=fe80::200:5eff:fe00:53fe", "duid=0x0003000100005e005301", "last-received-counter=0", "dynamic=false", "invalid=false", "disabled=false")
    }
    list("ipv6/dhcp-client/option") {
        row("name=client-fqdn", "code=39", "value=0x000864656d6f2d68617000")
    }
    list("ipv6/dhcp-relay") {
        row("name=vpn-relay6", "interface=wg-home", "dhcp-server=2001:db8:50::20", "link-address=fd66:66:66::1", "store-relayed-bindings=true", "dhcp-options=remote-id6", "delay-threshold=none", "requests=88", "responses=86", "invalid=false", "disabled=false")
        row("name=lab-relay6", "interface=bridge", "dhcp-server=2001:db8:50::21", "link-address=2001:db8:50::1", "store-relayed-bindings=false", "delay-threshold=none", "requests=0", "responses=0", "invalid=false", "disabled=true", "comment=standby, the local server lan6 answers on bridge")
    }
    list("ipv6/dhcp-relay/routes", writable = false) {
        row("relay=vpn-relay6", "prefix=2001:db8:50:f10::/60", "peer-address=fd66:66:66::2", "life-time=47m12s", "last-seen=3m40s", "dynamic=true")
    }
    list("ipv6/dhcp-relay/option") {
        row("name=remote-id6", "code=37", "value='demo-hap'", "only-if-mac-available=false", "raw-value=64656d6f2d686170")
    }
    list("ipv6/dhcp-server") {
        row("name=lan6", "interface=bridge", "prefix-pool=lan-pd", "address-pool=static-only", "lease-time=3d", "dhcp-option=sntp6", "dns-none=false", "use-radius=false", "rapid-commit=true", "use-reconfigure=false", "ignore-ia-na-bindings=false", "route-distance=1", "preference=0", "add-dns-entries=true", "add-dns-entries-suffix=lan", "allow-dual-stack-queue=true", "invalid=false", "disabled=false")
    }
    list("ipv6/dhcp-server/binding", fields = listOf(choice("ia-type", "na", "ta", "pd"))) {
        row("address=2001:db8:50:f00::/60", "duid=0x0003000100005e0053a2", "iaid=1", "ia-type=pd", "server=lan6", "life-time=2d23h41m", "prefix-pool=lan-pd", "status=bound", "expires-after=2d23h41m", "last-seen=18m", "allow-dual-stack-queue=true", "dynamic=true")
        row("address=2001:db8:50::20/128", "duid=0x0003000100005e005320", "iaid=1", "ia-type=na", "server=lan6", "life-time=3d", "rate-limit=", "allow-dual-stack-queue=true", "dynamic=false", "comment=NAS")
    }
    list("ipv6/dhcp-server/option") {
        row("name=sntp6", "code=31", "value=0x20010db8005000000000000000000001")
        row("name=search-lan", "code=24", "value=0x046C616E00")
    }
    list("ipv6/dhcp-server/option/sets") {
        row("name=lan6-extras", "options=sntp6,search-lan")
    }
}

private fun DemoBuilder.ipv6FirewallDemo() {
    val filterActions = choice("action", "accept", "drop", "reject", "jump", "log", "passthrough", "return")
    list("ipv6/firewall/filter", ordered = true, fields = listOf(ipv6Chains, ipv6Protocols, filterActions), live = counting()) {
        row("chain=input", "action=accept", "connection-state=established,related,untracked", "comment=defconf: accept established,related,untracked", "disabled=false")
        row("chain=input", "action=drop", "connection-state=invalid", "comment=defconf: drop invalid", "disabled=false")
        row("chain=input", "action=accept", "protocol=icmpv6", "comment=defconf: accept ICMPv6", "disabled=false")
        row("chain=input", "action=accept", "protocol=udp", "port=33434-33534", "comment=defconf: accept UDP traceroute", "disabled=false")
        row("chain=input", "action=accept", "protocol=udp", "src-address=fe80::/10", "dst-port=546", "comment=defconf: accept DHCPv6-Client prefix delegation", "disabled=false")
        row("chain=input", "action=accept", "protocol=udp", "dst-port=500,4500", "comment=defconf: accept IKE", "disabled=false")
        row("chain=input", "action=accept", "protocol=ipsec-ah", "comment=defconf: accept ipsec AH", "disabled=false")
        row("chain=input", "action=accept", "protocol=ipsec-esp", "comment=defconf: accept ipsec ESP", "disabled=false")
        row("chain=input", "action=accept", "ipsec-policy=in,ipsec", "comment=defconf: accept all that matches ipsec policy", "disabled=false")
        row("chain=input", "action=accept", "protocol=udp", "dst-port=13231", "in-interface-list=WAN", "comment=WireGuard", "disabled=false")
        row("chain=input", "action=accept", "protocol=tcp", "src-address=fd66:66:66::/64", "dst-port=22", "in-interface=wg-home", "comment=SSH over the tunnel", "disabled=false")
        row("chain=input", "action=drop", "in-interface-list=!LAN", "comment=defconf: drop everything else not coming from LAN", "disabled=false")
        row("chain=forward", "action=accept", "connection-state=established,related,untracked", "comment=defconf: accept established,related,untracked", "disabled=false")
        row("chain=forward", "action=drop", "connection-state=invalid", "comment=defconf: drop invalid", "disabled=false")
        row("chain=forward", "action=drop", "src-address-list=bad_ipv6", "comment=defconf: drop packets with bad src ipv6", "disabled=false")
        row("chain=forward", "action=drop", "dst-address-list=bad_ipv6", "comment=defconf: drop packets with bad dst ipv6", "disabled=false")
        row("chain=forward", "action=drop", "protocol=icmpv6", "hop-limit=equal:1", "comment=defconf: rfc4890 drop hop-limit=1", "disabled=false")
        row("chain=forward", "action=accept", "protocol=icmpv6", "comment=defconf: accept ICMPv6", "disabled=false")
        row("chain=forward", "action=accept", "protocol=139", "comment=defconf: accept HIP", "disabled=false")
        row("chain=forward", "action=accept", "protocol=udp", "dst-port=500,4500", "comment=defconf: accept IKE", "disabled=false")
        row("chain=forward", "action=accept", "protocol=ipsec-ah", "comment=defconf: accept ipsec AH", "disabled=false")
        row("chain=forward", "action=accept", "protocol=ipsec-esp", "comment=defconf: accept ipsec ESP", "disabled=false")
        row("chain=forward", "action=accept", "ipsec-policy=in,ipsec", "comment=defconf: accept all that matches ipsec policy", "disabled=false")
        row("chain=forward", "action=accept", "in-interface=wg-home", "out-interface-list=LAN", "comment=road warriors reach the LAN", "disabled=false")
        row("chain=forward", "action=accept", "protocol=tcp", "dst-address=2001:db8:50::20", "dst-port=443", "in-interface-list=WAN", "comment=NAS over IPv6", "disabled=false")
        row("chain=forward", "action=drop", "in-interface-list=!LAN", "comment=defconf: drop everything else not coming from LAN", "disabled=false")
        row("chain=output", "action=accept", "protocol=icmpv6", "out-interface=ether1", "comment=ICMPv6 to the provider", "disabled=false")
        row("chain=output", "action=drop", "protocol=tcp", "src-port=23", "out-interface-list=WAN", "comment=no telnet replies to the internet", "disabled=false")
    }
    list(
        "ipv6/firewall/nat",
        ordered = true,
        fields = listOf(choice("chain", "srcnat", "dstnat"), ipv6Protocols, choice("action", "accept", "masquerade", "dst-nat", "src-nat", "netmap", "redirect", "jump", "return")),
        live = counting(),
    ) {
        row("chain=srcnat", "action=masquerade", "src-address=fd66:66:66::/64", "out-interface-list=WAN", "comment=road warriors without global addresses", "disabled=false")
        row("chain=dstnat", "action=dst-nat", "protocol=tcp", "dst-port=8443", "in-interface-list=WAN", "to-address=2001:db8:50::20", "to-ports=443", "comment=NAS over IPv6 NAT", "disabled=false")
        row("chain=dstnat", "action=redirect", "protocol=udp", "dst-address=2001:db8:50::1", "dst-port=53", "src-address-list=admin6", "in-interface=bridge", "to-ports=53", "comment=DNS of the admin hosts stays on the router", "disabled=false")
        row("chain=srcnat", "action=masquerade", "protocol=tcp", "src-port=1024-65535", "port=22", "dst-address-list=admin6", "out-interface=wg-home", "comment=SSH from the router to the road warriors", "disabled=true")
    }
    list(
        "ipv6/firewall/mangle",
        ordered = true,
        fields = listOf(ipv6Chains, ipv6Protocols, choice("action", "accept", "change-mss", "change-hop-limit", "jump", "log", "mark-connection", "mark-packet", "mark-routing", "passthrough", "return", "set-priority")),
        live = counting(),
    ) {
        row("chain=forward", "action=change-mss", "new-mss=clamp-to-pmtu", "passthrough=true", "protocol=tcp", "tcp-flags=syn", "out-interface=wg-home", "comment=clamp MSS for WireGuard", "disabled=false")
        row("chain=forward", "action=mark-connection", "new-connection-mark=voip6", "passthrough=true", "protocol=udp", "port=5060", "dst-address=2001:db8:200::/48", "in-interface-list=LAN", "out-interface-list=WAN", "comment=SIP over IPv6", "disabled=false")
        row("chain=forward", "action=mark-packet", "connection-mark=voip6", "new-packet-mark=voip", "passthrough=false", "in-interface-list=LAN", "comment=VoIP packets", "disabled=false")
        row("chain=prerouting", "action=mark-routing", "new-routing-mark=guest", "passthrough=false", "src-address=2001:db8:50:89::/64", "in-interface=bridge", "comment=guest network uses its own routing table", "disabled=false")
        row("chain=prerouting", "action=mark-connection", "new-connection-mark=admin6", "passthrough=true", "src-address-list=admin6", "dst-address-list=!blocked6", "in-interface=bridge", "comment=admin sessions over IPv6", "disabled=false")
        row("chain=prerouting", "action=mark-packet", "new-packet-mark=dns6-replies", "passthrough=true", "protocol=udp", "src-port=53", "in-interface-list=WAN", "comment=DNS answers", "disabled=false")
        row("chain=prerouting", "action=mark-packet", "new-packet-mark=ntp6", "passthrough=true", "protocol=udp", "dst-port=123", "in-interface-list=LAN", "comment=NTP queries", "disabled=false")
    }
    list("ipv6/firewall/raw", ordered = true, fields = listOf(choice("chain", "prerouting", "output"), ipv6Protocols, choice("action", "accept", "drop", "jump", "log", "notrack", "passthrough", "return")), live = counting()) {
        row("chain=prerouting", "action=drop", "src-address-list=bad_ipv6", "in-interface-list=WAN", "comment=bogon sources on the WAN", "disabled=false")
        row("chain=prerouting", "action=drop", "src-address-list=blocked6", "in-interface=ether1", "comment=blocked hosts never reach connection tracking", "disabled=false")
        row("chain=prerouting", "action=drop", "src-address=2001:db8:bad::/48", "dst-address=2001:db8:50::/48", "in-interface=ether1", "comment=a hostile network", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=tcp", "dst-port=23,2323", "in-interface-list=WAN", "comment=telnet probes", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=udp", "src-port=0", "in-interface-list=WAN", "comment=UDP source port 0", "disabled=false")
        row("chain=prerouting", "action=drop", "protocol=udp", "port=1900", "in-interface-list=WAN", "comment=SSDP from the internet", "disabled=false")
        row("chain=output", "action=drop", "dst-address-list=blocked6", "out-interface=ether1", "comment=the router never talks to blocked addresses", "disabled=false")
        row("chain=output", "action=notrack", "protocol=udp", "dst-port=123", "out-interface-list=WAN", "comment=skip connection tracking for NTP", "disabled=false")
    }
    list("ipv6/firewall/connection", writable = false) {
        row("src-address=[2001:db8:50::20]:41822", "dst-address=[2001:db8:200::53]:53", "protocol=udp", "timeout=9s", "orig-rate=0bps", "repl-rate=0bps", "orig-packets=2", "repl-packets=2", "orig-bytes=148", "repl-bytes=420")
        row("src-address=[2001:db8:50:0:4d1e:92b6:7a05:c3d8]:51944", "dst-address=[2001:db8:100::1]:443", "protocol=tcp", "tcp-state=established", "timeout=23h59m52s", "orig-rate=1.2kbps", "repl-rate=4.8kbps", "orig-packets=318", "repl-packets=402", "orig-bytes=21904", "repl-bytes=411220")
        row("src-address=[2001:db8:50:0:200:5eff:fe00:5382]:49228", "dst-address=[2001:db8:300:1::80]:3478", "protocol=udp", "timeout=28s", "orig-rate=76kbps", "repl-rate=388kbps", "orig-packets=18821", "repl-packets=24012", "orig-bytes=1893211", "repl-bytes=29011888")
        row("src-address=[2001:db8:50:0:8c1a:6fd2:3b9:e741]:60112", "dst-address=[2001:db8:200::853]:853", "protocol=tcp", "tcp-state=established", "timeout=23h58m41s", "orig-rate=0bps", "repl-rate=0bps", "orig-packets=40", "repl-packets=38", "orig-bytes=4811", "repl-bytes=9220")
        row("src-address=2001:db8:50::20", "dst-address=2001:db8:100::1", "protocol=icmpv6", "timeout=9s", "orig-rate=0bps", "repl-rate=0bps", "orig-packets=1", "repl-packets=1", "orig-bytes=104", "repl-bytes=104")
    }
    list("ipv6/firewall/address-list") {
        row("list=bad_ipv6", "address=::/128", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: unspecified address")
        row("list=bad_ipv6", "address=::1/128", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: lo")
        row("list=bad_ipv6", "address=fec0::/10", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: site-local")
        row("list=bad_ipv6", "address=::ffff:0.0.0.0/96", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: ipv4-mapped")
        row("list=bad_ipv6", "address=100::/64", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: discard only")
        row("list=bad_ipv6", "address=3ffe::/16", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=defconf: 6bone")
        row("list=admin6", "address=2001:db8:50::101", "creation-time=2026-09-12 20:01:00", "dynamic=false", "disabled=false", "comment=admin laptop")
        row("list=blocked6", "address=2001:db8:bad::7", "timeout=11h22m", "creation-time=2026-10-31 07:12:44", "dynamic=true", "disabled=false")
    }
}

private fun DemoBuilder.ipv6DiscoveryDemo() {
    list("ipv6/nd", fields = listOf(choice("ra-preference", "low", "medium", "high"))) {
        row("interface=all", "ra-interval=3m20s-10m", "ra-delay=3s", "ra-preference=medium", "mtu=unspecified", "reachable-time=unspecified", "retransmit-interval=unspecified", "ra-lifetime=30m", "hop-limit=unspecified", "advertise-mac-address=true", "advertise-dns=true", "managed-address-configuration=false", "other-configuration=false", "default=true", "invalid=false", "disabled=false")
        row("interface=bridge", "ra-interval=1m-3m", "ra-delay=3s", "ra-preference=high", "mtu=1500", "reachable-time=30s", "retransmit-interval=1s", "ra-lifetime=30m", "hop-limit=64", "dns=2001:db8:50::1", "pref64=64:ff9b::/96", "advertise-mac-address=true", "advertise-dns=true", "managed-address-configuration=false", "other-configuration=true", "default=false", "invalid=false", "disabled=false", "comment=LAN")
    }
    list("ipv6/nd/prefix") {
        row("prefix=2001:db8:50::/64", "interface=bridge", "valid-lifetime=4w2d", "preferred-lifetime=1w", "on-link=true", "autonomous=true", "6to4-interface=none", "dynamic=true", "invalid=false", "disabled=false")
        row("prefix=fd66:66:66::/64", "interface=wg-home", "valid-lifetime=1d", "preferred-lifetime=4h", "on-link=true", "autonomous=false", "6to4-interface=none", "dynamic=false", "invalid=false", "disabled=false", "comment=ULA for road warriors")
    }
    settings("ipv6/nd/prefix/default", "autonomous=true", "on-link=true", "preferred-lifetime=1w", "valid-lifetime=4w2d")
    list("ipv6/nd/proxy") {
        row("interface=ether1", "address=2001:db8:ff01::/64", "disabled=true", "comment=not needed with the delegated prefix")
    }
    settings("ipv6/nd/settings", "router-advertisement-ignored-options=mtu", "router-advertisement-route-distance=1")
    list("ipv6/neighbor", writable = false) {
        row("address=fe80::200:5eff:fe00:5320", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:20", "status=reachable", "host-name=nas", "router=false", "dynamic=true")
        row("address=2001:db8:50::20", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:20", "status=reachable", "host-name=nas", "router=false", "dynamic=true")
        row("address=fe80::200:5eff:fe00:5365", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:65", "status=stale", "host-name=macbook-pro", "router=false", "dynamic=true")
        row("address=2001:db8:50:0:4d1e:92b6:7a05:c3d8", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:65", "status=reachable", "host-name=macbook-pro", "router=false", "dynamic=true")
        row("address=fe80::200:5eff:fe00:5382", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:82", "status=reachable", "host-name=ps5", "router=false", "dynamic=true")
        row("address=fe80::200:5eff:fe00:53a2", "interface=bridge", "vrf=main", "mac-address=00:00:5E:00:53:A2", "status=stale", "router=true", "dynamic=true")
        row("address=fe80::200:5eff:fe00:53fe", "interface=ether1", "vrf=main", "mac-address=00:00:5E:00:53:FE", "status=reachable", "router=true", "dynamic=true")
    }
    list("ipv6/route", fields = listOf(choice("routing-table", "main", "guest"))) {
        row("dst-address=::/0", "gateway=fe80::200:5eff:fe00:53fe%ether1", "immediate-gw=fe80::200:5eff:fe00:53fe%ether1", "distance=1", "routing-table=main", "scope=30", "target-scope=10", "check-gateway=none", "active=true", "dynamic=true", "ecmp=false", "hw-offloaded=false")
        row("dst-address=2001:db8:50::/64", "gateway=bridge", "immediate-gw=bridge", "distance=0", "routing-table=main", "pref-src=2001:db8:50::1", "scope=10", "target-scope=5", "active=true", "dynamic=true", "connect=true")
        row("dst-address=2001:db8:ff01::5/128", "gateway=ether1", "immediate-gw=ether1", "distance=0", "routing-table=main", "scope=10", "target-scope=5", "active=true", "dynamic=true", "connect=true")
        row("dst-address=fd66:66:66::/64", "gateway=wg-home", "immediate-gw=wg-home", "distance=0", "routing-table=main", "pref-src=fd66:66:66::1", "scope=10", "target-scope=5", "active=true", "dynamic=true", "connect=true")
        row("dst-address=2001:db8:50:f00::/60", "gateway=fe80::200:5eff:fe00:53a2%bridge", "immediate-gw=fe80::200:5eff:fe00:53a2%bridge", "distance=1", "routing-table=main", "scope=30", "target-scope=10", "active=true", "dynamic=true")
        row("dst-address=2001:db8:50::/48", "gateway=", "distance=254", "routing-table=main", "scope=30", "target-scope=10", "blackhole=true", "suppress-hw-offload=false", "active=true", "static=true", "comment=summary for the delegated prefix")
    }
}
