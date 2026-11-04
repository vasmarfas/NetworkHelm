package com.vasmarfas.networkhelm.demo

import com.vasmarfas.networkhelm.model.Capabilities
import com.vasmarfas.networkhelm.model.DeviceInfo
import com.vasmarfas.networkhelm.model.Platform
import com.vasmarfas.networkhelm.model.SafeModeKind
import com.vasmarfas.networkhelm.model.duration

private const val UPTIME = 8 * 86_400L + 19 * 3_600L + 5 * 60L

internal fun keeneticDemo(): DemoSession = demoSession(
    DeviceInfo(Platform.Keenetic, "Keenetic Demo", "4.3.1", "mips", "Keenetic Giga (KN-1011)", emptySet(), setOf("read", "write"), id = "demo-keenetic"),
    Capabilities(subscriptions = false, streamingCommands = false, cancel = false, safeMode = SafeModeKind.None, terminal = false, files = false, export = false, menuContents = false),
) {
    list("interface", writable = false) {
        row("id=ISP", "interface-name=GigabitEthernet1", "type=GigabitEthernet", "description=Provider", "link=up", "state=up", "mac=00:00:5e:00:53:71", "mtu=1500", "address=203.0.113.120", "mask=255.255.255.0", "uptime=8d19h")
        row("id=Bridge0", "interface-name=Home", "type=Bridge", "description=Home network", "link=up", "state=up", "mac=00:00:5e:00:53:72", "mtu=1500", "address=192.168.1.1", "mask=255.255.255.0", "uptime=8d19h")
        row("id=Bridge1", "interface-name=Guest", "type=Bridge", "description=Guest network", "link=up", "state=up", "mac=00:00:5e:00:53:73", "mtu=1500", "address=192.168.2.1", "mask=255.255.255.0", "uptime=8d19h")
        row("id=WifiMaster0", "interface-name=WifiMaster0", "type=WifiMaster", "description=2.4 GHz", "link=up", "state=up", "mac=00:00:5e:00:53:74", "mtu=1500", "address=", "mask=", "uptime=8d19h")
        row("id=WifiMaster1", "interface-name=WifiMaster1", "type=WifiMaster", "description=5 GHz", "link=up", "state=up", "mac=00:00:5e:00:53:75", "mtu=1500", "address=", "mask=", "uptime=8d19h")
        row("id=Wireguard0", "interface-name=Wireguard0", "type=Wireguard", "description=Office", "link=up", "state=up", "mac=", "mtu=1420", "address=10.8.0.2", "mask=255.255.255.0", "uptime=1d02h")
    }
    list("ip/arp", writable = false) {
        row("ip=192.168.1.101", "mac=00:00:5e:00:53:65", "interface=Bridge0")
        row("ip=192.168.1.102", "mac=00:00:5e:00:53:66", "interface=Bridge0")
        row("ip=192.168.1.121", "mac=00:00:5e:00:53:79", "interface=Bridge0")
        row("ip=192.168.2.14", "mac=00:00:5e:00:53:7a", "interface=Bridge1")
        row("ip=203.0.113.1", "mac=00:00:5e:00:53:fe", "interface=ISP")
    }
    list("ip/dhcp/bindings", writable = false) {
        row("ip=192.168.1.101", "mac=00:00:5e:00:53:65", "hostname=macbook-pro", "expires=10h31m")
        row("ip=192.168.1.102", "mac=00:00:5e:00:53:66", "hostname=iphone-anna", "expires=7h12m")
        row("ip=192.168.1.121", "mac=00:00:5e:00:53:79", "hostname=esp32-sensor", "expires=4h02m")
        row("ip=192.168.2.14", "mac=00:00:5e:00:53:7a", "hostname=guest-phone", "expires=0h48m")
    }
    list("ip/hotspot", writable = false) {
        row("name=macbook-pro", "hostname=macbook-pro", "ip=192.168.1.101", "mac=00:00:5e:00:53:65", "interface.name=Home", "active=true", "rxbytes=412809221", "txbytes=88120344", "uptime=3h12m")
        row("name=iphone-anna", "hostname=iphone-anna", "ip=192.168.1.102", "mac=00:00:5e:00:53:66", "interface.name=Home", "active=true", "rxbytes=18002133", "txbytes=2200112", "uptime=41m")
        row("name=esp32-sensor", "hostname=esp32-sensor", "ip=192.168.1.121", "mac=00:00:5e:00:53:79", "interface.name=Home", "active=true", "rxbytes=3204411", "txbytes=611900", "uptime=8d19h")
        row("name=guest-phone", "hostname=guest-phone", "ip=192.168.2.14", "mac=00:00:5e:00:53:7a", "interface.name=Guest", "active=false", "rxbytes=8802", "txbytes=4410", "uptime=0m")
    }
    list("ip/route", writable = false) {
        row("destination=0.0.0.0/0", "gateway=203.0.113.1", "interface=ISP", "metric=0")
        row("destination=192.168.1.0/24", "gateway=0.0.0.0", "interface=Bridge0", "metric=0")
        row("destination=192.168.2.0/24", "gateway=0.0.0.0", "interface=Bridge1", "metric=0")
        row("destination=10.8.0.0/24", "gateway=0.0.0.0", "interface=Wireguard0", "metric=0")
    }
    list("log", writable = false) {
        row("time=Oct 31 11:58:02", "ident=ndhcps", "message=client 00:00:5e:00:53:65 acknowledged 192.168.1.101")
        row("time=Oct 31 11:41:19", "ident=ndm", "message=Network::Interface::Wireguard: Wireguard0 peer handshake completed")
        row("time=Oct 31 09:00:05", "ident=ndm", "message=Core::Syslog: the system clock was synchronized with pool.ntp.org")
        row("time=Oct 30 22:14:40", "ident=ndm", "message=Core::Authenticator: user admin logged in via RCI from 192.168.1.101")
    }
    settings(
        "system/resource",
        "hostname=Keenetic Demo", "board-name=Keenetic Giga (KN-1011)", "version=4.3.1", "architecture-name=mips", "uptime=${duration(UPTIME)}", "cpu-load=7",
        "free-memory=268435456", "total-memory=536870912",
        writable = false,
        live = { _, tick -> mapOf("uptime" to duration(UPTIME + tick), "cpu-load" to "${wander(7, tick, 4)}", "free-memory" to "${wander(268_435_456, tick, 9)}") },
    )
}
