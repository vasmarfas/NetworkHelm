package com.vasmarfas.networkhelm.demo

internal fun DemoBuilder.controllerDemo() {
    settings(
        "cmr",
        "enabled=true", "pairing-requirement=confirm", "controller-addresses=192.168.88.1", "track-topology=true", "fetch-comments=true", "auto-labels=all",
        "apptraffic-devices=gateway", "upgrade-check-interval=10h", "packages-directory=", "packages-cache-type=memory", "packages-cache-limit=128",
        fields = listOf(choice("pairing-requirement", "none", "password", "confirm"), choice("packages-cache-type", "directory", "memory")),
    )
    settings(
        "cmr/client",
        "enabled=false", "pairing-requirement=password", "controller-addresses=", "status=disabled", "pairing-status=", "controller-identity=", "controller-address=",
        "comment=settings ignored because used by local controller",
        fields = listOf(choice("pairing-requirement", "none", "password", "confirm")),
    )

    list("cmr/device", fields = listOf(choice("channel", "long-term", "stable", "testing", "development"))) {
        row(
            "identity=demo-hap", "address=192.168.88.1", "board=hAP ax^3", "version=7.24.1", "pairing-status=paired", "connected=false", "connected-time=", "available-version=", "labels=gateway",
            "controller=true", "upgrade-available=false", "pending=false", "channel=stable", "active-alerts=", "port-labels=ether1:uplink", "comment=this router",
        )
        row(
            "identity=switch-closet", "address=192.168.88.2", "board=CSS610-8G-2S+", "version=7.24.1", "pairing-status=paired", "connected=true", "connected-time=12d9h4m", "available-version=", "labels=switch",
            "controller=false", "upgrade-available=false", "pending=false", "channel=stable", "active-alerts=", "port-labels=ether1:uplink",
        )
        row(
            "identity=cap-attic", "address=192.168.88.3", "board=cAP ax", "version=7.23.2", "pairing-status=paired", "connected=true", "connected-time=3d4h12m", "available-version=7.24.1", "labels=ap",
            "controller=false", "upgrade-available=true", "pending=false", "channel=stable", "active-alerts=upgrade-available", "port-labels=ether1:uplink",
        )
        row(
            "identity=hap-ax2-bedroom", "address=192.168.88.4", "board=hAP ax^2", "version=7.24.1", "pairing-status=paired", "connected=true", "connected-time=8d1h30m", "available-version=", "labels=ap",
            "controller=false", "upgrade-available=false", "pending=false", "channel=stable", "active-alerts=", "port-labels=ether1:uplink",
        )
        row(
            "identity=wap-ac-garage", "address=192.168.88.5", "board=wAP ac", "version=7.22.1", "pairing-status=paired", "connected=false", "connected-time=", "available-version=", "labels=ap,garage",
            "controller=false", "upgrade-available=false", "pending=false", "channel=stable", "active-alerts=device-offline", "port-labels=ether1:uplink",
        )
        row(
            "identity=crs-rack", "address=192.168.88.6", "board=CRS326-24G-2S+", "version=7.23.2", "pairing-status=paired", "connected=true", "connected-time=41d2h18m", "available-version=7.24.1", "labels=switch",
            "controller=false", "upgrade-available=true", "pending=false", "channel=stable", "active-alerts=upgrade-available", "port-labels=sfp-sfpplus1:uplink",
        )
        row(
            "identity=hex-garage", "address=192.168.88.7", "board=hEX S", "version=7.24.1", "pairing-status=paired", "connected=true", "connected-time=19d6h41m", "available-version=", "labels=garage,router",
            "controller=false", "upgrade-available=false", "pending=false", "channel=stable", "active-alerts=cpu-temperature", "port-labels=ether1:uplink",
        )
        row(
            "identity=cap-ax-workshop", "address=192.168.88.8", "board=cAP ax", "version=7.24.1", "pairing-status=pending", "connected=true", "connected-time=2m14s", "available-version=", "labels=",
            "controller=false", "upgrade-available=false", "pending=true", "channel=stable", "active-alerts=", "port-labels=", "comment=waiting for the pairing to be confirmed",
        )
    }
    list("cmr/alert", fields = listOf(choice("severity", "critical", "high", "medium", "low"))) {
        row(
            "name=cpu-load", "labels=all", "severity=high", "category=performance", "devices=7", "devices-on=0", "fired=2", "action-failures=0",
            "cpu-above=95", "action.log=[device] CPU usage is above 95% ([cpu-usage]%)", "action.log-topics=cmr,warning", "disabled=false",
        )
        row(
            "name=cpu-temperature", "labels=garage", "severity=high", "category=performance", "devices=2", "devices-on=1", "fired=3", "action-failures=0",
            "health-value=cpu-temperature", "health-above=60", "action.log=[device] CPU temperature is above 60 C", "action.log-topics=cmr,warning", "disabled=false",
        )
        row(
            "name=device-offline", "labels=all", "severity=critical", "category=availability", "devices=7", "devices-on=1", "fired=4", "action-failures=0",
            "disconnected-more-than=5m", "action.log=[device] has been disconnected for more than 5 minutes", "action.log-topics=cmr,error", "disabled=false",
        )
        row(
            "name=disk-full", "labels=all", "severity=medium", "category=performance", "devices=7", "devices-on=0", "fired=0", "action-failures=0",
            "hdd-above=90", "action.log=[device] disk usage is above 90%", "action.log-topics=cmr,warning", "disabled=false",
        )
        row(
            "name=memory-low", "labels=all", "severity=medium", "category=performance", "devices=7", "devices-on=0", "fired=1", "action-failures=0",
            "mem-above=90", "action.log=[device] memory usage is above 90%", "action.log-topics=cmr,warning", "disabled=false",
        )
        row(
            "name=upgrade-available", "labels=all", "severity=low", "category=version", "devices=7", "devices-on=2", "fired=2", "action-failures=0",
            "upgrade-available=true", "action.log=[device] has a newer RouterOS version available", "action.log-topics=cmr,info", "disabled=false",
        )
        row(
            "name=rebooted", "labels=all", "severity=medium", "category=availability", "devices=7", "devices-on=0", "fired=5", "action-failures=0",
            "rebooted=true", "action.log=[device] has rebooted", "action.log-topics=cmr,info", "disabled=false",
        )
        row(
            "name=new-device", "labels=", "severity=low", "category=network", "devices=0", "devices-on=0", "fired=1", "action-failures=0",
            "unpaired-device-connected=true", "action.log=a new device is waiting to be paired", "action.log-topics=cmr,info", "disabled=false",
        )
        row(
            "name=wifi-interface-change", "labels=ap", "severity=medium", "category=network", "devices=3", "devices-on=0", "fired=7", "action-failures=0",
            "interface-change=not-running", "interface-type=wifi", "action.log=[device] has a WiFi interface that stopped running", "action.log-topics=cmr,warning", "disabled=true",
        )
    }

    list("cmr/layout") {
        row("name=Home", "scale=100", "file=")
        row("name=Garage", "scale=100", "file=")
    }
    list("cmr/layout/node") {
        row("name=demo-hap", "layout=Home", "device=demo-hap", "target-layout=", "x=0", "y=-170")
        row("name=switch-closet", "layout=Home", "device=switch-closet@192.168.88.2", "target-layout=", "x=0", "y=-40")
        row("name=crs-rack", "layout=Home", "device=crs-rack@192.168.88.6", "target-layout=", "x=-250", "y=100")
        row("name=hap-ax2-bedroom", "layout=Home", "device=hap-ax2-bedroom@192.168.88.4", "target-layout=", "x=250", "y=100")
        row("name=cap-attic", "layout=Home", "device=cap-attic@192.168.88.3", "target-layout=", "x=-250", "y=240")
        row("name=To_Garage", "layout=Home", "device=", "target-layout=Garage", "x=0", "y=240")
        row("name=hex-garage", "layout=Garage", "device=hex-garage@192.168.88.7", "target-layout=", "x=-110", "y=0")
        row("name=wap-ac-garage", "layout=Garage", "device=wap-ac-garage@192.168.88.5", "target-layout=", "x=110", "y=0")
    }
    list("cmr/layout/link") {
        row("layout=Home", "node1=demo-hap", "node2=switch-closet", "links=*ether5(,tx=18.4MiB,rx=4.2MiB)--*ether1(,tx=4.2MiB,rx=18.4MiB)")
        row("layout=Home", "node1=switch-closet", "node2=crs-rack", "links=*sfp-sfpplus1(,tx=1.1GiB,rx=912.3MiB)--*sfp-sfpplus1(,tx=912.3MiB,rx=1.1GiB)")
        row("layout=Home", "node1=switch-closet", "node2=hap-ax2-bedroom", "links=*ether8(,tx=3.8MiB,rx=1.2MiB)--*ether1(,tx=1.2MiB,rx=3.8MiB)")
        row("layout=Home", "node1=crs-rack", "node2=cap-attic", "links=*ether24(poe=powered-on,tx=220.5KiB,rx=96.0KiB)--*ether1(,tx=96.0KiB,rx=220.5KiB)")
        row("layout=Home", "node1=crs-rack", "node2=To_Garage", "links=")
        row("layout=Garage", "node1=hex-garage", "node2=wap-ac-garage", "links=*ether5(poe=waiting-for-load,tx=0,rx=0)--*ether1(,tx=0,rx=0)")
    }

    list("cmr/vlan", ordered = true, fields = listOf(choice("role", "access", "trunk"))) {
        row("labels=switch", "port-labels=ether7,ether8", "vlan-ids=89", "role=access", "disabled=false", "comment=guest ports")
        row("labels=switch", "port-labels=ether6", "vlan-ids=20", "role=access", "disabled=false", "comment=IoT port")
        row("labels=switch", "port-labels=sfp-sfpplus1", "vlan-ids=20,89", "role=trunk", "disabled=true", "comment=uplink between the switches")
    }
    list("cmr/wifi", fields = listOf(choice("mode", "ap", "station", "station-bridge", "station-pseudobridge"))) {
        row("labels=ap,+2ghz", "ssid=DemoNet", "mode=ap", "vlan-id=", "security.authentication-types=wpa2-psk,wpa3-psk", "security.encryption=ccmp", "disabled=false", "comment=house 2.4 GHz")
        row("labels=ap,+5ghz", "ssid=DemoNet-5G", "mode=ap", "vlan-id=", "security.authentication-types=wpa2-psk,wpa3-psk", "security.encryption=ccmp", "disabled=false", "comment=house 5 GHz")
        row("labels=ap", "ssid=DemoGuest", "mode=ap", "vlan-id=89", "security.authentication-types=wpa2-psk", "security.encryption=ccmp", "disabled=false", "comment=guest network")
        row("labels=garage", "ssid=Garage-Temp", "mode=ap", "vlan-id=", "security.authentication-types=wpa2-psk", "security.encryption=ccmp", "disabled=true", "comment=for the summer, off")
    }
    list("cmr/upgrade", ordered = true, fields = listOf(choice("strategy", "sequential", "parallel"), choice("fail-policy", "stop", "continue", "continue-order"))) {
        row("name=switches", "labels=switch", "channel=stable", "schedule-time=03:00:00@sat", "strategy=sequential", "fail-policy=stop", "order=", "dynamic=false", "disabled=false")
        row("name=access-points", "labels=ap", "channel=stable", "schedule-time=04:00:00@sun", "strategy=parallel", "fail-policy=continue", "order=", "dynamic=false", "disabled=false")
        row("name=default", "labels=all", "channel=stable", "schedule-time=", "strategy=sequential", "fail-policy=stop", "order=", "dynamic=true", "disabled=false")
    }
}
