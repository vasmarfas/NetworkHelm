package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.duration

internal fun DemoBuilder.routingDemo() {
    routingPolicyDemo()
    routingBgpDemo()
    routingOspfDemo()
    routingOtherDemo()
    routingMulticastDemo()
    routingMplsDemo()
}

private fun DemoBuilder.routingPolicyDemo() {
    list("routing/table") {
        row("name=main", "fib=true", "dynamic=false", "invalid=false", "disabled=false")
        row("name=guest", "fib=true", "dynamic=false", "invalid=false", "disabled=false", "comment=guest network")
        row("name=lab", "fib=true", "dynamic=true", "invalid=false", "disabled=false")
    }
    list("routing/rule", ordered = true, fields = listOf(choice("action", "lookup", "lookup-only-in-table", "drop", "unreachable"))) {
        row("dst-address=192.168.88.0/24", "chain=user", "action=lookup", "table=main", "min-prefix=0", "vrf=main", "invalid=false", "disabled=false", "comment=LAN stays in the main table")
        row("src-address=192.168.89.0/24", "chain=user", "action=lookup", "table=guest", "min-prefix=0", "invalid=false", "disabled=false", "comment=guest network")
        row("routing-mark=guest", "chain=user", "action=lookup-only-in-table", "table=guest", "invalid=false", "disabled=false")
        row("interface=wg-home", "chain=user", "action=lookup", "table=main", "min-prefix=0", "realm=0", "invalid=false", "disabled=false", "comment=road warriors")
    }
    list("routing/nexthop", writable = false) {
        row("afi=ip", "address=203.0.113.1", "scope=10", "target-scope=10", "check-gateway=none", "immediate-gw.address=203.0.113.1%ether1", "immediate-gw.weight=1", "immediate-gw.flap-count=0")
        row("afi=ip", "address=192.168.88.254", "scope=10", "target-scope=10", "check-gateway=none", "immediate-gw.address=192.168.88.254%bridge", "immediate-gw.weight=1", "immediate-gw.flap-count=1", "immediate-gw.mpls-peer-id=10.20.0.1:0", "immediate-gw.mpls-label=implicit-null")
        row("afi=ipv6", "address=fe80::200:5eff:fe00:53fe", "scope=10", "target-scope=10", "check-gateway=none", "immediate-gw.address=fe80::200:5eff:fe00:53fe%ether1", "immediate-gw.weight=1", "immediate-gw.flap-count=0")
    }
    list("routing/id") {
        row("name=main", "id=192.168.88.1", "select-dynamic-id=", "select-from-vrf=main", "dynamic-id=192.168.88.1", "disabled=false")
    }
    settings(
        "routing/settings", "single-process=false", "check-gateway-ping-interval=10s", "check-gateway-ping-timeout=1s", "check-gateway-ping-count=2",
        "policy-rules=user", "connected-in-chain=", "dynamic-in-chain=",
    )
    list("routing/filter/rule", ordered = true) {
        row("chain=ospf-in", "rule=if (dst in 10.20.0.0/16) { accept }", "disabled=false")
        row("chain=ospf-in", "rule=reject", "disabled=false")
        row("chain=ospf-out", "rule=if (dst in 192.168.88.0/24 || dst in 10.66.66.0/24) { accept }", "disabled=false")
        row("chain=ospf-out", "rule=reject", "disabled=false")
        row("chain=bgp-in", "rule=if (dst-len > 24) { reject }", "disabled=false")
        row("chain=bgp-in", "rule=if (dst in 192.0.2.0/24) { set distance 20; accept }", "disabled=false")
        row("chain=bgp-in", "rule=reject", "disabled=false")
        row("chain=bgp-out", "rule=if (dst in 192.168.88.0/24) { set bgp-communities 64511:100; accept }", "disabled=false")
        row("chain=bgp-out", "rule=if (dst in 10.66.66.0/24) { accept }", "disabled=false")
        row("chain=bgp-out", "rule=reject", "disabled=false")
        row("chain=rip-in", "rule=accept", "disabled=false")
        row("chain=rip-out", "rule=if (dst in 192.168.88.0/24) { accept }", "disabled=false")
        row("chain=rip-out", "rule=reject", "disabled=false")
    }
    list("routing/filter/select-rule", ordered = true) {
        row("chain=bgp-select", "do-where=dst in 192.168.88.0/24", "do-take=true", "disabled=false")
        row("chain=bgp-select", "do-where=dst in 10.66.66.0/24", "do-jump=bgp-extra-select", "disabled=false")
        row("chain=bgp-extra-select", "do-where=dst-len == 24", "do-select-num=1", "do-select-prfx=24", "disabled=false")
        row("chain=bgp-extra-select", "do-group-num=2", "do-group-prfx=24", "disabled=false")
    }
    list("routing/filter/num-list") {
        row("list=private-as", "range=64512-65534")
        row("list=documentation-as", "range=64496-64511")
        row("list=branch-asns", "range=64510")
    }
    list("routing/filter/community-list") {
        row("list=customers", "communities=64511:100,64511:200")
        row("list=blackhole", "communities=65535:666")
        row("list=local", "regexp=^64511:")
    }
    list("routing/filter/community-ext-list") {
        row("list=rt-branch", "communities=rt:64511:100")
        row("list=soo-office", "communities=soo:64511:10")
        row("list=any-rt", "regexp=^rt:64511:")
    }
    list("routing/filter/community-large-list") {
        row("list=large-demo", "communities=64511:1:100")
        row("list=large-any", "regexp=^64511:")
    }
}

private fun DemoBuilder.routingBgpDemo() {
    list("routing/bgp/instance") {
        row("name=bgp-main", "as=64511", "router-id=main", "routing-table=main", "vrf=main", "cluster-id=", "always-compare-med=false", "ignore-as-path-len=false", "multipath=false", "disabled=false")
    }
    list(
        "routing/bgp/template",
        fields = listOf(choice("afi", "ip", "ipv6", "l2vpn", "vpnv4"), choice("nexthop-choice", "default", "force-self", "propagate")),
    ) {
        row("name=default", "as=64511", "routing-table=main", "default=true", "inactive=false", "disabled=false")
        row("name=ebgp-branch", "as=64511", "afi=ip", "router-id=main", "routing-table=main", "vrf=main", "multihop=true", "hold-time=3m", "keepalive-time=1m", "use-bfd=false", "input.filter=bgp-in", "input.allow-as=0", "input.ignore-as-path-len=false", "input.accept-unknown=false", "output.filter-chain=bgp-out", "output.redistribute=connected", "output.default-originate=never", "output.as-override=false", "output.default-prepend=0", "output.remove-private-as=false", "output.no-client-to-client-reflection=false", "output.keep-sent-attributes=false", "output.no-early-cut=false", "nexthop-choice=default", "default=false", "inactive=false", "disabled=false")
    }
    list("routing/bgp/connection", fields = listOf(choice("local.role", "ebgp", "ibgp", "ibgp-rr", "ibgp-rr-client", "ebgp-customer", "ebgp-provider"))) {
        row("name=branch-office", "instance=bgp-main", "templates=ebgp-branch", "as=64511", "afi=ip", "router-id=main", "remote.address=192.0.2.1", "remote.port=179", "remote.as=64510", "local.address=192.168.88.1", "local.role=ebgp", "local.default-address=192.168.88.1", "tcp-md5-key=demo-md5-key", "multihop=true", "local.ttl=255", "remote.ttl=1", "connect=true", "listen=true", "hold-time=3m", "keepalive-time=1m", "use-bfd=false", "routing-table=main", "vrf=main", "input.filter=bgp-in", "output.filter-chain=bgp-out", "output.redistribute=connected", "nexthop-choice=default", "inactive=false", "disabled=false", "comment=over the IPsec tunnel to the branch office")
        row("name=backup-transit", "instance=bgp-main", "templates=default", "as=64511", "afi=ip", "router-id=main", "remote.address=198.51.100.1", "remote.port=179", "remote.as=64496", "local.address=203.0.113.50", "local.role=ebgp", "local.default-address=203.0.113.50", "multihop=false", "connect=true", "listen=false", "hold-time=3m", "keepalive-time=1m", "use-bfd=true", "routing-table=main", "vrf=main", "inactive=true", "disabled=true", "comment=prepared, the provider has not enabled the peering")
    }
    list(
        "routing/bgp/session",
        writable = false,
        live = { _, tick ->
            mapOf(
                "uptime" to duration(11_300L + tick), "remote.messages" to "${312L + tick / 60}", "local.messages" to "${312L + tick / 60}",
                "remote.bytes" to "${6_420L + tick / 60 * 19}", "local.bytes" to "${6_118L + tick / 60 * 19}",
            )
        },
    ) {
        row("name=branch-office-1", "remote.address=192.0.2.1", "remote.as=64510", "remote.id=192.0.2.1", "remote.capabilities=mp,rr,as4", "remote.afi=ip", "remote.messages=312", "remote.bytes=6420", "remote.eor=ip", "local.address=192.168.88.1", "local.as=64511", "local.id=192.168.88.1", "local.capabilities=mp,rr,gr,as4", "local.afi=ip", "local.messages=312", "local.bytes=6118", "local.eor=ip", "output.procid=20", "input.procid=20", "ebgp=true", "hold-time=3m", "keepalive-time=1m", "uptime=3h8m20s", "last-started=2026-10-31 06:33:12", "prefix-count=2", "established=true")
    }
    list("routing/bgp/advertisements", writable = false) {
        row("peer=branch-office-1", "prefix=192.168.88.0/24", "nexthop=192.168.88.1", "origin=incomplete", "as-path=64511", "communities=64511:100")
        row("peer=branch-office-1", "prefix=10.66.66.0/24", "nexthop=192.168.88.1", "origin=incomplete", "as-path=64511")
    }
    list("routing/bgp/vpn", fields = listOf(choice("label-allocation-policy", "per-prefix", "per-vrf"))) {
        row("name=vpn-lab", "instance=bgp-main", "vrf=lab", "route-distinguisher=64511:200", "label-allocation-policy=per-vrf", "export.redistribute=connected", "export.route-targets=64511:200", "import.route-targets=64511:200", "disabled=false")
    }
    list("routing/bgp/evpn") {
        row("name=evpn-lab", "instance=bgp-main", "vni=100", "rd=64511:300", "vrf=lab", "export.route-targets=64511:300", "import.route-targets=64511:300", "disabled=true")
    }
    list("routing/bgp/vpls", fields = listOf(choice("pw-type", "raw-ethernet", "tagged-ethernet", "vpls"), choice("pw-control-word", "default", "disabled", "enabled"))) {
        row("name=bgp-vpls-1", "vrf=main", "rd=64511:100", "site-id=1", "cisco-id=0", "import-route-targets=64511:100", "export-route-targets=64511:100", "local-pref=100", "bridge=bridge", "bridge-cost=50", "bridge-horizon=1", "bridge-pvid=1", "pw-type=raw-ethernet", "pw-l2mtu=1500", "pw-control-word=default", "disabled=true")
    }
    list("routing/bfd/configuration", ordered = true) {
        row("interfaces=bridge", "addresses=192.168.88.254/32", "vrf=main", "min-tx=200ms", "min-rx=200ms", "multiplier=5", "forbid-bfd=false", "disabled=false")
        row("interfaces=wg-home", "address-list=admin", "vrf=main", "min-tx=1s", "min-rx=1s", "multiplier=5", "forbid-bfd=true", "disabled=false", "comment=no BFD towards road warriors")
    }
    list("routing/bfd/session", writable = false) {
        row("multihop=false", "remote-address=192.168.88.254", "local-address=192.168.88.1", "interface=bridge", "state=up", "state-changes=1", "desired-tx-interval=200ms", "required-min-rx=200ms", "remote-min-rx=200ms", "multiplier=5", "packets-rx=1284112", "packets-tx=1284090", "up=true", "inactive=false")
    }
}

private fun DemoBuilder.routingOspfDemo() {
    list("routing/ospf/instance", fields = listOf(choice("version", "2", "3"), choice("originate-default", "always", "if-installed", "never"))) {
        row("name=default", "version=2", "router-id=main", "vrf=main", "routing-table=main", "redistribute=connected,static", "originate-default=never", "in-filter-chain=ospf-in", "out-filter-chain=ospf-out", "use-dn=false", "mpls-te-address=192.168.88.1", "mpls-te-area=backbone", "inactive=false", "disabled=false")
        row("name=v6", "version=3", "router-id=main", "vrf=main", "routing-table=main", "redistribute=connected", "originate-default=never", "use-dn=false", "inactive=false", "disabled=false", "comment=OSPFv3 for the IPv6 prefixes")
    }
    list("routing/ospf/area", fields = listOf(choice("type", "default", "stub", "nssa"), choice("nssa-translator", "candidate", "always", "never"))) {
        row("name=backbone", "instance=default", "area-id=0.0.0.0", "type=default", "default-cost=1", "no-summaries=false", "nssa-translator=candidate", "inactive=false", "disabled=false")
        row("name=branch-stub", "instance=default", "area-id=0.0.0.5", "type=stub", "default-cost=10", "no-summaries=true", "nssa-translator=candidate", "inactive=false", "disabled=false")
        row("name=backbone-v6", "instance=v6", "area-id=0.0.0.0", "type=default", "default-cost=1", "no-summaries=false", "nssa-translator=candidate", "inactive=false", "disabled=false")
    }
    list("routing/ospf/area/range") {
        row("area=backbone", "prefix=192.168.88.0/24", "advertise=true", "cost=10", "disabled=false")
        row("area=branch-stub", "prefix=10.20.0.0/16", "advertise=true", "cost=calculated", "disabled=false")
    }
    list("routing/ospf/interface-template", fields = listOf(choice("type", "broadcast", "ptp", "ptmp", "nbma", "ptp-unnumbered"), choice("auth", "off", "simple", "md5", "sha1", "sha256"))) {
        row("interfaces=bridge", "networks=192.168.88.0/24", "area=backbone", "type=broadcast", "cost=10", "priority=128", "passive=false", "instance-id=0", "auth=md5", "auth-id=1", "auth-key=demo-ospf-key", "use-bfd=true", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "inactive=false", "disabled=false")
        row("interfaces=wg-home", "networks=10.66.66.0/24", "area=backbone", "type=ptp", "cost=50", "priority=128", "passive=true", "instance-id=0", "auth=off", "use-bfd=false", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "inactive=false", "disabled=false", "comment=advertised only")
        row("interfaces=bridge", "networks=2001:db8:50::/64", "area=backbone-v6", "type=broadcast", "cost=10", "priority=128", "passive=false", "instance-id=0", "auth=off", "use-bfd=false", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "inactive=false", "disabled=false")
    }
    list("routing/ospf/interface", writable = false) {
        row("instance=default", "address=192.168.88.1%bridge", "area=backbone", "state=bdr", "network-type=broadcast", "cost=10", "priority=128", "dr=192.168.88.254", "bdr=192.168.88.1", "use-bfd=true", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "dynamic=true")
        row("instance=default", "address=10.66.66.1%wg-home", "area=backbone", "state=ptp", "network-type=ptp", "cost=50", "priority=128", "use-bfd=false", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "dynamic=true")
        row("instance=v6", "address=fe80::200:5eff:fe00:5302%bridge", "area=backbone-v6", "state=bdr", "network-type=broadcast", "cost=10", "priority=128", "dr=10.20.0.1", "bdr=192.168.88.1", "use-bfd=false", "retransmit-interval=5s", "transmit-delay=1s", "hello-interval=10s", "dead-interval=40s", "dynamic=true")
    }
    list("routing/ospf/neighbor", writable = false) {
        row("instance=default", "area=backbone", "address=192.168.88.254", "priority=200", "router-id=10.20.0.1", "dr=192.168.88.254", "bdr=192.168.88.1", "state=Full", "state-changes=5", "ls-retransmits=0", "adjacency=1d6h42m", "timeout=33s", "dynamic=true")
        row("instance=v6", "area=backbone-v6", "address=fe80::200:5eff:fe00:53a2%bridge", "priority=200", "router-id=10.20.0.1", "dr=10.20.0.1", "bdr=192.168.88.1", "state=Full", "state-changes=3", "ls-retransmits=0", "adjacency=1d6h40m", "timeout=36s", "dynamic=true")
    }
    list("routing/ospf/static-neighbor") {
        row("address=192.168.88.253", "area=backbone", "instance-id=0", "poll-interval=2m", "disabled=false", "comment=standby office router")
    }
    list("routing/ospf/lsa", writable = false) {
        row("instance=default", "area=backbone", "type=router", "id=192.168.88.1", "originator=192.168.88.1", "sequence=0x8000001B", "age=412", "checksum=0xA4F3", "self-originated=true", "body=link: type=transit id=192.168.88.254 data=192.168.88.1 metric=10; link: type=stub id=10.66.66.0 data=255.255.255.0 metric=50")
        row("instance=default", "area=backbone", "type=router", "id=10.20.0.1", "originator=10.20.0.1", "sequence=0x80000031", "age=655", "checksum=0x7C12", "self-originated=false", "body=link: type=transit id=192.168.88.254 data=192.168.88.254 metric=10; link: type=stub id=10.20.0.0 data=255.255.0.0 metric=10")
        row("instance=default", "area=backbone", "type=network", "id=192.168.88.254", "originator=10.20.0.1", "sequence=0x8000000C", "age=655", "checksum=0x31D8", "self-originated=false", "body=mask=255.255.255.0 attached routers: 10.20.0.1 192.168.88.1")
        row("instance=default", "area=backbone", "type=summary", "id=10.20.0.0", "originator=10.20.0.1", "sequence=0x80000004", "age=1203", "checksum=0xE5A0", "self-originated=false", "body=mask=255.255.0.0 metric=10")
        row("instance=default", "area=backbone", "type=external", "id=0.0.0.0", "originator=10.20.0.1", "sequence=0x80000002", "age=1744", "checksum=0x19B7", "self-originated=false", "body=mask=0.0.0.0 type=2 metric=1 forwarding=0.0.0.0")
    }
}

private fun DemoBuilder.routingOtherDemo() {
    list("routing/rip/instance", fields = listOf(choice("afi", "ip", "ipv6"), choice("originate-default", "always", "if-installed", "never"))) {
        row("name=rip-legacy", "vrf=main", "afi=ip", "in-filter-chain=rip-in", "out-filter-chain=rip-out", "redistribute=connected", "originate-default=never", "routing-table=main", "route-timeout=3m", "route-gc-timeout=2m", "update-interval=30s", "disabled=false", "comment=old switch that only speaks RIP")
    }
    list("routing/rip/interface-template", fields = listOf(choice("mode", "default", "passive", "send-only", "receive-only"))) {
        row("instance=rip-legacy", "interfaces=bridge", "source-addresses=192.168.88.0/24", "cost=1", "split-horizon=true", "poison-reverse=false", "mode=default", "use-bfd=false", "key-chain=rip-keys", "disabled=false")
    }
    list("routing/rip/interface", writable = false) {
        row("instance=rip-legacy", "interface=bridge", "address=192.168.88.1", "passive=false", "dynamic=true")
    }
    list("routing/rip/neighbor", writable = false) {
        row("instance=rip-legacy", "address=192.168.88.253", "routes=3", "packets-total=1204", "packets-bad=0", "entries-bad=0", "dynamic=true")
    }
    list("routing/rip/static-neighbor") {
        row("instance=rip-legacy", "address=192.168.88.253", "disabled=false")
    }
    list("routing/rip/keys") {
        row("chain=rip-keys", "key-id=1", "key=demo-rip-key", "valid-from=2026-01-01 00:00:00", "valid-till=2027-01-01 00:00:00", "disabled=false")
    }
    list("routing/isis/instance") {
        row("name=isis-lab", "system-id=0000.0000.0001", "afi=ip", "areas=49.0001", "areas-max=3", "metric-type=wide", "vrf=main", "l1.originate-default=never", "l1.lsp-max-age=20m", "l1.lsp-max-size=1492", "l1.lsp-refresh-interval=15m", "l1.lsp-update-interval=30s", "l2.originate-default=never", "l2.lsp-max-age=20m", "l2.lsp-max-size=1492", "l2.lsp-refresh-interval=15m", "l2.lsp-update-interval=30s", "disabled=true", "comment=experiments, not in use")
    }
    list("routing/rpki") {
        row("group=rpki-main", "vrf=main", "address=192.0.2.53", "port=323", "preference=1", "refresh-interval=1h", "retry-interval=10m", "expire-interval=2h", "disabled=false")
    }
    list("routing/rpki/session", writable = false) {
        row("group=rpki-main", "address=192.0.2.53", "port=323", "state=established", "version=1", "session=41219", "serial=7712", "expires=1h58m")
    }
}

private fun DemoBuilder.routingMulticastDemo() {
    settings("routing/igmp-proxy", "robustness=2", "query-interval=2m5s", "query-response-interval=10s", "last-member-query-interval=1s", "quick-leave=false")
    list("routing/igmp-proxy/interface") {
        row("interface=ether1", "upstream=true", "threshold=1", "alternative-subnets=0.0.0.0/0", "robustness=2", "query-interval=2m5s", "query-response-interval=10s", "last-member-query-interval=1s", "state.querier=false", "state.rx-bytes=18211904", "state.rx-packets=14211", "state.tx-bytes=2211", "state.tx-packets=34", "state.robustness=2", "state.query-interval=2m5s", "state.source-ip-address=203.0.113.50", "disabled=false")
        row("interface=bridge", "upstream=false", "threshold=1", "robustness=2", "query-interval=2m5s", "query-response-interval=10s", "last-member-query-interval=1s", "state.querier=true", "state.rx-bytes=4211", "state.rx-packets=61", "state.tx-bytes=88120344", "state.tx-packets=71200", "state.robustness=2", "state.query-interval=2m5s", "state.source-ip-address=192.168.88.1", "disabled=false")
    }
    list("routing/igmp-proxy/mfc", writable = false) {
        row("group=239.192.10.1", "source=203.0.113.30", "upstream-interface=ether1", "downstream-interfaces=bridge", "active-downstream-interfaces=bridge", "bytes=88211900", "packets=71200", "wrong-packets=0", "dynamic=true")
        row("group=239.192.10.2", "source=203.0.113.30", "upstream-interface=ether1", "downstream-interfaces=bridge", "active-downstream-interfaces=", "bytes=0", "packets=0", "wrong-packets=0", "dynamic=true")
    }
    list("routing/gmp") {
        row("interfaces=bridge", "groups=239.192.10.0/24", "exclude=false", "sources=203.0.113.30", "disabled=false")
    }
    list("routing/pimsm/instance", fields = listOf(choice("afi", "ipv4", "ipv6"))) {
        row("name=pim-main", "vrf=main", "afi=ipv4", "switch-to-spt=true", "switch-to-spt-interval=1m40s", "switch-to-spt-bytes=0", "crp-advertise-contained=false", "bsm-forward-back=false", "rp-hash-mask-length=30", "rp-static-override=false", "ssm-range=232.0.0.0/8", "disabled=false")
    }
    list("routing/pimsm/interface-template", ordered = true) {
        row("instance=pim-main", "interfaces=bridge", "source-addresses=0.0.0.0/0", "hello-period=30s", "hello-delay=5s", "priority=1", "join-prune-period=1m", "propagation-delay=500ms", "override-interval=2s500ms", "join-tracking-support=true", "disabled=false")
    }
    list("routing/pimsm/igmp-interface-template", ordered = true) {
        row("instance=pim-main", "interfaces=bridge", "robustness=2", "query-interval=2m5s", "query-response-interval=10s", "last-member-query-interval=1s", "disabled=false")
    }
    list("routing/pimsm/interface", writable = false) {
        row("instance=pim-main", "interface=bridge", "address=192.168.88.1", "pim.priority=1", "pim.propagation-delay=500ms", "pim.override-interval=2s500ms", "designated-router=true", "join-tracking=true", "dynamic=true")
    }
    list("routing/pimsm/neighbor", writable = false) {
        row("instance=pim-main", "address=192.168.88.254", "priority=1", "propagation-delay=500ms", "override-interval=2s500ms", "designated-router=false", "join-tracking=true", "timeout=1m38s", "dynamic=true")
    }
    list("routing/pimsm/static-rp") {
        row("instance=pim-main", "group=239.192.0.0/16", "address=192.168.88.254", "disabled=false")
    }
    list("routing/pimsm/uib-g", writable = false) {
        row("instance=pim-main", "group=239.192.10.1", "rp=192.168.88.254", "rpf=192.168.88.254%bridge", "rp-local=false", "dynamic=true")
    }
    list("routing/pimsm/uib-sg", writable = false) {
        row("instance=pim-main", "group=239.192.10.1", "source=203.0.113.30", "rpf=203.0.113.1%ether1", "register=no-info", "keepalive=3m", "spt-bit=true", "dynamic=true")
    }
    list("routing/pimsm/bsr", writable = false) {
        row("instance=pim-main", "scope4=239.0.0.0/8", "address=192.168.88.254", "priority=64", "hash-mask-length=30", "state=accept-preferred", "dynamic=true")
    }
    list("routing/pimsm/bsr/rp-set", writable = false) {
        row("instance=pim-main", "group=239.0.0.0/8", "rp.address=192.168.88.254", "rp.priority=192", "rp.timeout=2m10s", "dynamic=true")
    }
    list("routing/pimsm/bsr/candidate") {
        row("instance=pim-main", "address=192.168.88.1", "scope4=239.0.0.0/8", "priority=1", "hash-mask-length=30", "state=candidate", "disabled=false")
    }
    list("routing/pimsm/bsr/rp-candidate") {
        row("instance=pim-main", "group=239.192.0.0/16", "address=192.168.88.1", "priority=192", "holdtime=2m30s", "disabled=false")
    }
}

private fun DemoBuilder.routingMplsDemo() {
    settings(
        "mpls/settings", "dynamic-label-range=16-1048575", "propagate-ttl=true", "allow-fast-path=true", "mpls-fast-path-packets=61204", "mpls-fast-path-bytes=48211904",
        live = { _, tick -> mapOf("mpls-fast-path-packets" to "${61_204L + tick * 7L}", "mpls-fast-path-bytes" to "${48_211_904L + tick * 6_300L}") },
    )
    list("mpls/ldp", fields = listOf(choice("preferred-afi", "ip", "ipv6"))) {
        row("lsr-id=192.168.88.1", "vrf=main", "afi=ip", "preferred-afi=ip", "hop-limit=255", "path-vector-limit=255", "loop-detect=false", "use-explicit-null=false", "distribute-for-default=false", "transport-addresses=192.168.88.1", "dynamic=false", "disabled=false")
    }
    list("mpls/ldp/interface") {
        row("interface=bridge", "hello-interval=5s", "hold-time=15s", "transport-addresses=192.168.88.1", "accept-dynamic-neighbors=true", "afi=ip", "dynamic=false", "disabled=false")
    }
    list("mpls/ldp/neighbor", writable = false) {
        row("transport=192.168.88.254", "local-transport=192.168.88.1", "peer=10.20.0.1:0", "send-targeted=false", "addresses=192.168.88.254,10.20.0.1", "path-vector-limit=255", "on-demand=false", "used-afi=ip", "dynamic=true", "operational=true")
    }
    list("mpls/ldp/accept-filter", ordered = true) {
        row("vrf=main", "prefix=10.20.0.0/16", "neighbor=all", "accept=true", "disabled=false")
        row("vrf=main", "prefix=0.0.0.0/0", "neighbor=all", "accept=false", "disabled=false")
    }
    list("mpls/ldp/advertise-filter", ordered = true) {
        row("vrf=main", "prefix=192.168.88.0/24", "neighbor=all", "advertise=true", "disabled=false")
        row("vrf=main", "prefix=0.0.0.0/0", "neighbor=all", "advertise=false", "disabled=false")
    }
    list("mpls/ldp/local-mapping", writable = false) {
        row("vrf=main", "dst-address=192.168.88.0/24", "label=implicit-null", "peers=10.20.0.1:0", "dynamic=true")
        row("vrf=main", "dst-address=10.66.66.0/24", "label=implicit-null", "peers=10.20.0.1:0", "dynamic=true")
        row("vrf=main", "dst-address=10.20.0.0/16", "label=17", "adv-path=10.20.0.1", "peers=10.20.0.1:0", "dynamic=true")
        row("vrf=main", "dst-address=10.20.0.1/32", "label=18", "adv-path=10.20.0.1", "peers=10.20.0.1:0", "dynamic=true")
    }
    list("mpls/ldp/remote-mapping", writable = false) {
        row("vrf=main", "dst-address=10.20.0.0/16", "label=implicit-null", "nexthop=192.168.88.254", "peer=10.20.0.1:0", "path=10.20.0.1", "dynamic=true")
        row("vrf=main", "dst-address=10.20.0.1/32", "label=implicit-null", "nexthop=192.168.88.254", "peer=10.20.0.1:0", "path=10.20.0.1", "dynamic=true")
        row("vrf=main", "dst-address=192.168.88.254/32", "label=implicit-null", "nexthop=192.168.88.254", "peer=10.20.0.1:0", "path=10.20.0.1", "dynamic=true")
    }
    list("mpls/interface", ordered = true) {
        row("interface=all", "mpls-mtu=1500", "input=true", "disabled=false")
        row("interface=bridge", "mpls-mtu=1508", "input=true", "disabled=false")
    }
    list("mpls/mangle") {
        row("chain=input", "exp=5", "set-mark=mpls-voice", "packets=12", "disabled=false")
        row("chain=output", "set-exp=5", "set-mark=mpls-voice", "packets=401", "disabled=false")
    }
    list("mpls/forwarding-table", writable = false) {
        row("label=17", "type=ldp", "vrf=main", "prefix=10.20.0.0/16", "nexthops=192.168.88.254%bridge,label=implicit-null", "ldp=true", "traffic-eng=false", "vpn=false", "dynamic=true")
        row("label=18", "type=ldp", "vrf=main", "prefix=10.20.0.1/32", "nexthops=192.168.88.254%bridge,label=implicit-null", "ldp=true", "traffic-eng=false", "vpn=false", "dynamic=true")
        row("label=19", "type=vpls", "vrf=main", "vpls=vpls-office", "ldp=false", "traffic-eng=false", "vpn=false", "dynamic=true")
        row("label=20", "type=traffic-eng", "vrf=main", "te-sender=192.168.88.1", "te-session=192.168.88.1->10.20.0.1:1", "nexthops=192.168.88.254%bridge,label=implicit-null", "ldp=false", "traffic-eng=true", "vpn=false", "dynamic=true")
    }
    list("mpls/traffic-eng/interface") {
        row("interface=bridge", "bandwidth=1Gbps", "te-metric=10", "k-factor=3", "resource-class=0", "refresh-time=45s", "use-udp=false", "blockade-k-factor=3", "igp-flood-period=3m", "up-flood-thresholds=15,30,45,60,75,80,85,90,95,96,97,98,99,100", "down-flood-thresholds=100,99,98,97,96,95,90,85,80,75,60,45,30,15", "remaining-bw=900Mbps", "remaining-bw-prios=900Mbps,900Mbps,900Mbps,900Mbps,900Mbps,900Mbps,900Mbps,1Gbps", "lih=1", "local-address-ip=192.168.88.1", "disabled=false")
    }
    list("mpls/traffic-eng/path") {
        row("name=to-office", "use-cspf=true", "setup-priority=7", "holding-priority=7", "record-route=false", "affinity-include-all=0", "affinity-include-any=0", "affinity-exclude=0", "reoptimize-interval=none", "hops=192.168.88.254:strict,10.20.0.1:loose", "disabled=false")
    }
    list("mpls/traffic-eng/tunnel") {
        row("name=te-office", "vrf=main", "from-address=192.168.88.1", "to-address=10.20.0.1", "bandwidth=100Mbps", "primary-path=to-office", "secondary-standby=false", "primary-retry-interval=1m", "setup-priority=7", "holding-priority=7", "record-route=false", "affinity-include-all=0", "affinity-include-any=0", "affinity-exclude=0", "reoptimize-interval=none", "bandwidth-limit=0", "auto-bandwidth-avg-interval=5m", "auto-bandwidth-update-interval=1h", "forwarding-on=primary", "primary=established", "disabled=false")
    }
    list("mpls/traffic-eng/flow", writable = false) {
        row("vrf=main", "session=192.168.88.1->10.20.0.1", "sender=192.168.88.1", "label=20", "out-labels=implicit-null", "out-nexthop=192.168.88.254", "bw=100Mbps", "dynamic=true")
    }
    list("interface/vpls", fields = listOf(choice("pw-type", "raw-ethernet", "tagged-ethernet", "vpls"), choice("pw-control-word", "default", "disabled", "enabled"))) {
        row("name=vpls-office", "type=vpls", "mtu=1500", "actual-mtu=1500", "l2mtu=1500", "mac-address=00:00:5E:00:53:B1", "arp=enabled", "arp-timeout=auto", "peer=10.20.0.1", "vpls-id=100:1", "cisco-static-id=0", "bridge=bridge", "bridge-cost=50", "bridge-horizon=1", "bridge-pvid=1", "pw-type=raw-ethernet", "pw-l2mtu=1500", "pw-control-word=default", "bgp-vpls=none", "running=true", "disabled=false", "last-link-down-time=2026-10-27 22:03:40", "last-link-up-time=2026-10-27 22:04:12", "link-downs=1", "remote-label=24", "local-label=19", "remote-group=0", "tx-bits-per-second=61200", "rx-bits-per-second=88400", "tx-packets-per-second=12", "rx-packets-per-second=15", "tx-byte=88211904", "rx-byte=121904118", "tx-packet=88120", "rx-packet=121904")
    }
}
