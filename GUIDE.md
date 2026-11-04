# Guide

This is what you need to get started: how to install the program, how to connect to a router, what to switch on at the router itself, and what to do when it does not work. There are no descriptions of individual windows. The program reads menus and fields from the router, and the meaning of the settings is explained by the makers; for MikroTik that is the RouterOS documentation. The project as a whole is described in [README.md](README.md).

**Русский:** [GUIDE.ru.md](GUIDE.ru.md)

## Installing

### Windows

Run the installer (`windows-setup.exe`) or the portable file (`windows-portable.exe`, no install needed). If the file has no publisher signature, Windows shows "Windows protected your PC": press "More info", then "Run anyway".

On its first start the portable file unpacks itself into `%LOCALAPPDATA%\NetworkHelm\portable\<version>` (about 200 MB) and keeps its settings in the `NetworkHelm data` folder beside it. If the installer or the portable file says the program does not support this version of Windows, you have Windows 7, 8 or 8.1. There are files marked `legacy` for them.

### macOS

Open the DMG and drag NetworkHelm into Applications. If the program is not signed by Apple, macOS refuses to open it the first time. Open System Settings, Privacy & Security, find the message about NetworkHelm and press "Open Anyway". When macOS asks about access to the local network, allow it: without it the program cannot reach the routers of your network.

### Linux

DEB and RPM go in through the package manager:

```bash
sudo apt install ./NetworkHelm-<version>-linux-amd64.deb
sudo dnf install ./NetworkHelm-<version>-linux-x86_64.rpm
```

The AppImage is not installed: make the file executable (`chmod +x`) and run it. It mounts itself through FUSE and needs `fusermount` from the `fuse3` or `fuse` package; without FUSE, start it with `--appimage-extract-and-run`.

### Android

Download the APK and open it; Android asks for permission to install from that source. On the first connection allow "Nearby devices" for the program. Without it Android keeps the program out of the local network, and instead of a login you see "Android does not let NetworkHelm reach the local network".

### iOS

There is no ready app, only an Xcode project. On a Mac with Xcode run `tools/ios/build-ssh.sh` once, then open `app/iosApp` and build the app for your own device.

## First connection

The connection window opens on start. Its fields:

| Field | What to enter |
| --- | --- |
| Address | the name or IP address of the router; add a port after a colon if it is not the standard one |
| Platform | the type of device; the program recognises it by itself, and then "recognized" stands beside it |
| Protocol | the way to connect; grey entries are ports that gave no answer |
| Port | change it only if the service on the router listens on a non-standard port |
| User, Password | the account on the router |
| Remember the password | keeps the password in the protected storage of the system |
| Group, Comment | for order in the list of saved routers |
| Via router | connect through the SSH of another saved router, when the one you need cannot be reached directly |

As soon as you type an address, the program checks almost at once which ports of the router answer. It only tries to open a connection and reads the greeting, without logging in; from the answers it recognises the device and picks a protocol. You can change the choice by hand. A router that guards against password guessing may count such a check as a login attempt and block your address. Then turn the check off in "Settings": the item "Recognize the device and check which protocols answer while the address is typed".

Press "Connect". The "Save" button puts the router in the list on the "Saved" tab, so next time you only pick it there. The "Neighbors" tab shows the MikroTik routers that announce themselves on your network. They do it every 30 to 60 seconds, and "Refresh" asks them to answer now.

If the connection goes over a protocol without encryption (the API on port 8728, HTTP, SNMP v2c), a warning appears under the fields and the main window carries a "Not encrypted" mark. The password and all data then cross the network in the clear, so work like that only on a network you trust.

To open a configuration without a router, press "Open config file" or drop the file onto the connection window: an `/export` of RouterOS, an OpenWrt backup or a UCI file will do.

## Preparing the router

The program reaches a router through services it already has. If the service it needs is off or closed by the firewall, the connection fails and the program says which port gave no answer.

### MikroTik RouterOS

The services are visible in WinBox and in the console: `/ip service print`. Any of these will do:

| Way | Port | Notes |
| --- | --- | --- |
| WinBox | 8291 | the console carries the whole interface, so no API, REST or SSH is needed; the program picks it first when the port answers |
| API-SSL | 8729 | encrypted; needs a certificate: without one RouterOS answers with anonymous TLS, which the program does not use |
| API | 8728 | not encrypted |
| REST over HTTPS and HTTP | 443 and 80 | RouterOS 7.1 and later only, the `www-ssl` and `www` services |
| MAC-Telnet | UDP 20561 | for a router without an IP address; needs its MAC address and a shared layer 2 network |

A new MikroTik usually has the login `admin`, with an empty password or one printed on the sticker. If the user group lacks the rights for a menu section, the program marks the section and says which right is missing.

To move to encryption, first connect another way. If the first attempt gives an error about TLS (API-SSL is on and has no certificate), pick "API (8728)" in the "Protocol" list. When the connection opens, click the "Not encrypted" mark in the main window: the "Prepare router" window opens. It has four steps, each of which you can tick or untick, and the commands run when you press Apply:

- issue a self-signed certificate and turn on API-SSL (8729);
- allow the API through an SSH tunnel (`forwarding-enabled=local`);
- add the user group `networkhelm` with the rights the program needs; a separate user for the program can go in it instead of `admin`;
- turn off the plain API (8728) and Telnet; while this connection goes over API 8728 the step is unavailable, since it would cut the connection.

After that connect over API-SSL; the program asks about the fingerprint of the certificate once.

### OpenWrt

Pick the OpenWrt platform. There are two ways:

- SSH, port 22. It needs the root password or a key. On a freshly flashed router you set the password in LuCI, under System, Administration.
- ubus over HTTP (port 80) or HTTPS (port 443). It needs LuCI with the ubus module of the web server and rpcd; you log in as root with its password.

At the top of the main window you can switch the menu view: "OpenWrt view", "Unified view" (the menus the program has for any router) and "LuCI view".

### OPNsense

Create an API key on the router: System, Access, Users, the user you need, API keys. A file with the key and the secret downloads. In the program enter the key in "User" and the secret in "Password". The port is 443.

### pfSense

The package pfSense-pkg-RESTAPI version 2 has to be installed: without it pfSense does not answer the API. There are two ways to log in: leave "User" empty and enter a key from System, REST API, Keys as the password, or give the login and password of a local user.

### VyOS 1.4 and later

Turn on the HTTP API and create a key: `set service https api keys id <name> key <key>`, then `commit` and `save`. In the program leave "User" empty and enter the key as the password. Every edit is applied and saved at once.

### Keenetic and Netcraze

Read only. Enter the login and password of the web interface, or leave the login empty and put an access token of KeeneticOS 5.2 (the section "Users and access") in the password. These drivers are written from the documentation and have not been tried on a real device.

### Other devices

Pick "Other device". SSH is enough for Linux. SNMP v2c reads interfaces, addresses and routes; the community goes in the "Password" field. SNMP v3 needs a user name and passwords. UPnP and TR-064 find the device on port 1900 (a FRITZ!Box serves its description on port 49000). The program writes nothing over SNMP, UPnP or TR-064.

## Safety

If a router changes its certificate or its SSH key, the program tells you: it remembers the fingerprints of the certificates and keys you accepted. Protocols without encryption are marked in the connection window and in the main window. "Settings" has "Hide passwords and keys" (they stand as dots in tables, dialogs and the journal, and the eye in a field shows the value) and "Ask before removing from the router".

### Safe Mode

Safe Mode protects against an edit after which the router stops answering. How it works depends on the router.

On RouterOS press Safe Mode in the top bar. While it is on, the router takes the settings back by itself if the connection is lost. The button turns into a menu with a count of edits: "Keep changes" keeps them, "Roll back changes" undoes them. If you close the connection without choosing, the program asks whether to keep the edits.

On VyOS the button turns on commit-confirm for every edit. If the program is left without a connection after an edit, VyOS reboots in about a minute with the configuration saved before it.

On OpenWrt, OPNsense and pfSense the top bar has an Apply button instead of Safe Mode: it puts the edits you made into effect. On OpenWrt the apply goes through `uci apply` with a rollback, and if the connection is lost after it, the router returns to its previous configuration. On OPNsense 24.7 the rules of the firewall are rolled back, and the API of 26.7 has no rollback. pfSense has none at all. Where there is no way back, the program says so.

## In a browser

A browser cannot open connections to routers on its own, so a gateway stands between the two. It keeps the saved routers and their passwords and connects to the routers in place of the page.

**From the desktop app.** "Settings", "Access from a browser…". Set a password (you log in as `admin`), choose a port and press "Open the access". The window shows the addresses of the computer and the fingerprint of the certificate; there is a QR code for a phone. Open the address in the browser of a phone or of another computer on the same network. The browser warns once about the self-signed certificate: compare its SHA-256 fingerprint with the one in the window and go on. While the access is open, its port shows in the title of the window, and the next start of the app opens it again. The box "Any address, not only the local networks" is for access from outside the home network (through a forwarded port or a VPN); the gateway then asks for a code from an authenticator app every time (Google Authenticator, Aegis, 2FAS and the like).

**As a separate gateway.** The archive of the gateway (`NetworkHelm-gateway-<version>-windows-x64.zip` or `-linux-x64.tar.gz`) unpacks and runs without any Java installed: `networkhelm-gateway.bat` on Windows, `./networkhelm-gateway.sh` on Linux. The password of the first start shows once, in the console window or in the log; if you lose it, set a new one with the `--reset-password` option. The gateway also runs as a jar (Java 17 or newer is needed): `java -jar NetworkHelm-gateway-<version>.jar`; the address of the Docker image, if one was published, is in the description of the release. Then open `https://<address of the computer>:8443`. By default the gateway answers local networks only; the `--public` option opens it to any address and makes the code compulsory, and `--code` asks for the code on the local network as well.

**Straight on an OpenWrt router.** A router with LuCI can serve the page itself and reach only itself through `/ubus`; a page served that way has no terminal and no files.

More about the gateway (all options, Docker, a container on a RouterOS router, a systemd service) is in [server/README.md](server/README.md).

**The demo page.** https://vasmarfas.github.io/networkhelm/ shows the interface on made-up routers. It has no gateway, and a page in a browser can only send HTTP, so it cannot reach most real routers. How that page is built and what it can do is in [app/webApp/README.md](app/webApp/README.md).

## When something does not work

| What you see | What it means | What to do |
| --- | --- | --- |
| "… does not accept connections on port N: the service is off or the port is closed" | the service on the router is off, or the firewall closes the port | turn the service on (on RouterOS, for example, `/ip service enable api`) or pick another protocol; grey entries in the list are the ports that gave no answer |
| "… does not answer" | a wrong address, no route, or a firewall that silently drops packets | check the address, ping the router, make sure you are on the same network; if the router sits behind another router, use the "Via router" field |
| "… cannot be found" | the name does not resolve to an address | enter the IP address |
| "The router refused the login: …" | a wrong login or password, or the user has no rights for that service | check them and, on RouterOS, the user group |
| "… and NetworkHelm could not agree on TLS …" | API-SSL is on in RouterOS, but the service has no certificate | pick "API (8728)", connect, and issue a certificate in the "Prepare router" window |
| "Android does not let NetworkHelm reach the local network" | the permission is not granted | in the Android settings of the app allow "Nearby devices" |
| "The connection was lost after sending the command …" | the link dropped at the moment of the edit | check the router first: the command may have run, and a repeat would run it twice |
| The router stopped answering right after you typed the address | its protection against password guessing took the port check for a login attempt | wait for the block to end and turn the check off in "Settings" |
| "The browser blocked the request …" | a page in a browser can only send HTTP | open the app through a gateway |
| The program does not start | on a computer it writes the cause to the file `NetworkHelm-startup-error.log` in the temporary folder | take the file (`%TEMP%` on Windows, `/tmp` on Linux) and attach it to your report with the version of the system |

Everything else can go to the [issue tracker](https://github.com/vasmarfas/networkhelm/issues). Leave addresses, passwords and keys out of your reports.

## Where the data lives

The program keeps saved routers, settings and table layouts in its own data folder. Passwords and keys you asked it to remember are in the protected storage of the system: Windows Data Protection on Windows, the Keychain on macOS and iOS, the Secret Service on Linux, the Android Keystore on Android. Where there is no system storage, the program encrypts them itself, and that protection is weaker. All of it is described in [privacy-policy.md](privacy-policy.md).
