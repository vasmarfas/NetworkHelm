package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.Capabilities
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.SafeModeKind

private val actions = choice("action", "pass", "block", "reject")

internal fun opnsenseDemo(): DemoSession = demoSession(
    DeviceInfo(Platform.Opnsense, "demo-fw.lan", "25.1.5", "amd64", "OPNsense", emptySet(), setOf("read", "write"), id = "demo-opnsense"),
    Capabilities(subscriptions = false, streamingCommands = false, cancel = false, safeMode = SafeModeKind.None, terminal = false, files = false, export = false, menuContents = false),
    ids = { "7d0c1a5e-4b2f-4c3a-8e11-" + it.toString(16).padStart(12, '0') },
) {
    list("interfaces/overview", writable = false) {
        row("description=WAN", "device=igc0", "status=up", "link_type=dhcp", "ipv4=203.0.113.80/24", "ipv6=", "gateways=WAN_DHCP", "macaddr=00:00:5e:00:53:41", "mtu=1500", "media=1000baseT <full-duplex>")
        row("description=LAN", "device=igc1", "status=up", "link_type=static", "ipv4=10.10.0.1/24", "ipv6=fd00:10::1/64", "gateways=", "macaddr=00:00:5e:00:53:42", "mtu=1500", "media=1000baseT <full-duplex>")
        row("description=IoT", "device=igc1_vlan20", "status=up", "link_type=static", "ipv4=10.10.20.1/24", "ipv6=", "gateways=", "macaddr=00:00:5e:00:53:42", "mtu=1500", "media=1000baseT <full-duplex>")
        row("description=WireGuard", "device=wg0", "status=up", "link_type=static", "ipv4=10.99.0.1/24", "ipv6=", "gateways=", "macaddr=", "mtu=1420", "media=")
    }
    list("interfaces/vlan") {
        row("vlanif=igc1_vlan20", "if=igc1", "tag=20", "pcp=0", "proto=802.1q", "descr=IoT")
    }
    list("firewall/filter", ordered = true, fields = listOf(actions, choice("direction", "in", "out"), choice("ipprotocol", "inet", "inet6", "inet46"), choice("protocol", "TCP", "UDP", "TCP/UDP", "ICMP", "any"))) {
        row("sequence=1", "action=pass", "interface=lan", "direction=in", "ipprotocol=inet", "protocol=any", "source_net=lan", "destination_net=any", "description=Default allow LAN to any rule", "disabled=false")
        row("sequence=2", "action=pass", "interface=lan", "direction=in", "ipprotocol=inet6", "protocol=any", "source_net=lan", "destination_net=any", "description=Default allow LAN IPv6 to any rule", "disabled=false")
        row("sequence=10", "action=pass", "interface=wan", "direction=in", "ipprotocol=inet", "protocol=UDP", "source_net=any", "destination_net=wan", "destination_port=51820", "description=WireGuard", "disabled=false")
        row("sequence=20", "action=pass", "interface=iot", "direction=in", "ipprotocol=inet", "protocol=UDP", "source_net=iot", "destination_net=10.10.0.1", "destination_port=53", "description=IoT DNS", "disabled=false")
        row("sequence=21", "action=block", "interface=iot", "direction=in", "ipprotocol=inet", "protocol=any", "source_net=iot", "destination_net=lan", "description=IoT may not reach LAN", "disabled=false")
        row("sequence=30", "action=pass", "interface=wan", "direction=in", "ipprotocol=inet", "protocol=TCP", "source_net=any", "destination_net=10.10.0.20", "destination_port=443", "description=NAS over HTTPS", "disabled=true")
    }
    list("firewall/source-nat", fields = listOf(choice("protocol", "TCP", "UDP", "TCP/UDP", "ICMP", "any"))) {
        row("sequence=100", "interface=wan", "ipprotocol=inet", "protocol=any", "source_net=10.10.0.0/16", "destination_net=any", "target=wanip", "description=LAN to WAN", "disabled=false")
    }
    list("firewall/alias") {
        row("name=admins", "type=host", "content=10.10.0.101\n10.10.0.102", "description=Admin workstations", "current_items=2", "last_updated=")
        row("name=web_ports", "type=port", "content=80\n443\n8443", "description=Web", "current_items=3", "last_updated=")
        row("name=blocklist", "type=url", "content=https://feodotracker.abuse.ch/downloads/ipblocklist.txt", "description=Feodo", "current_items=312", "last_updated=2026-10-31T06:00:04+03:00")
    }
    list("routes/routes") {
        row("network=10.20.0.0/16", "gateway=LAN_GW", "descr=Branch office", "disabled=false")
        row("network=192.0.2.0/24", "gateway=WAN_DHCP", "descr=Partner", "disabled=true")
    }
    list("wireguard/server") {
        row("name=roadwarrior", "interface=wg0", "port=51820", "tunneladdress=10.99.0.1/24", "pubkey=U7hQ0sVx2aKd9cLm4NpRz1WfYeT8bJgHoI6nXtMqA3E=", "peers=phone,laptop", "disabled=false")
    }
    list("wireguard/client") {
        row("name=phone", "tunneladdress=10.99.0.2/32", "serveraddress=", "serverport=", "pubkey=Fn3Yk8TzQw1LcP5xVbR7uGdHs0AeJmOi9NtXq2KhWvE=", "servers=roadwarrior", "disabled=false")
        row("name=laptop", "tunneladdress=10.99.0.3/32", "serveraddress=", "serverport=", "pubkey=Zc6Vm1RtGp4JsKo8BwXn3QyAeL0uHdIf7NxTq5MhOaE=", "servers=roadwarrior", "disabled=false")
    }
    list("services/dhcp-leases", writable = false) {
        row("address=10.10.0.101", "hwaddr=00:00:5e:00:53:65", "hostname=macbook-pro", "starts=2026/10/31 09:12:04", "ends=2026/10/31 21:12:04", "state=active", "if=igc1")
        row("address=10.10.0.102", "hwaddr=00:00:5e:00:53:66", "hostname=iphone-anna", "starts=2026/10/31 10:40:11", "ends=2026/10/31 22:40:11", "state=active", "if=igc1")
        row("address=10.10.20.14", "hwaddr=00:00:5e:00:53:79", "hostname=esp32-sensor", "starts=2026/10/31 08:00:57", "ends=2026/10/31 20:00:57", "state=active", "if=igc1_vlan20")
    }
    list("unbound/host-override") {
        row("hostname=nas", "domain=lan", "rr=A", "server=10.10.0.20", "description=NAS", "enabled=1")
        row("hostname=printer", "domain=lan", "rr=A", "server=10.10.0.30", "description=Printer", "enabled=1")
    }
    list("services/services", writable = false) {
        row("name=unbound", "description=Unbound DNS", "running=1", "locked=0")
        row("name=dhcpd", "description=ISC DHCPv4 Server", "running=1", "locked=0")
        row("name=openssh", "description=Secure Shell Daemon", "running=1", "locked=0")
        row("name=wireguard", "description=WireGuard", "running=1", "locked=0")
        row("name=ntpd", "description=Network Time Daemon", "running=1", "locked=0")
    }
    list("diagnostics/arp", writable = false) {
        row("ip=10.10.0.101", "mac=00:00:5e:00:53:65", "intf_description=LAN", "hostname=macbook-pro", "manufacturer=", "expires=1186")
        row("ip=10.10.0.102", "mac=00:00:5e:00:53:66", "intf_description=LAN", "hostname=iphone-anna", "manufacturer=", "expires=912")
        row("ip=10.10.20.14", "mac=00:00:5e:00:53:79", "intf_description=IoT", "hostname=esp32-sensor", "manufacturer=", "expires=1108")
        row("ip=203.0.113.1", "mac=00:00:5e:00:53:fe", "intf_description=WAN", "hostname=", "manufacturer=", "expires=1200")
    }
    list("log/system", writable = false) {
        row("time=2026-10-31T11:58:02+03:00", "severity=Informational", "program=dhcpd", "message=DHCPACK on 10.10.0.101 to 00:00:5e:00:53:65 (macbook-pro) via igc1")
        row("time=2026-10-31T11:15:39+03:00", "severity=Notice", "program=wireguard", "message=wg0: peer Fn3Yk8TzQw1L handshake completed")
        row("time=2026-10-31T09:00:01+03:00", "severity=Informational", "program=configd.py", "message=[7a41] Reloading filter")
        row("time=2026-10-31T06:00:04+03:00", "severity=Informational", "program=filter", "message=alias blocklist refreshed: 312 entries")
        row("time=2026-10-30T22:14:40+03:00", "severity=Notice", "program=audit", "message=user root authenticated successfully via api from 10.10.0.101")
    }
    list("log/firewall", writable = false) {
        row("time=2026-10-31T11:58:14+03:00", "action=block", "interface=wan", "direction=in", "protocol=tcp", "source=198.51.100.7:55871", "destination=203.0.113.80:22", "flags=S", "rule=Default deny / state violation rule")
        row("time=2026-10-31T11:57:02+03:00", "action=block", "interface=iot", "direction=in", "protocol=tcp", "source=10.10.20.14:41822", "destination=10.10.0.20:445", "flags=S", "rule=IoT may not reach LAN")
        row("time=2026-10-31T11:41:50+03:00", "action=pass", "interface=wan", "direction=in", "protocol=udp", "source=198.51.100.77:51820", "destination=203.0.113.80:51820", "flags=", "rule=WireGuard")
    }
}
