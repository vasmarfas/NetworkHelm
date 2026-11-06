# Privacy Policy

Last updated: 4 November 2026

NetworkHelm is a client for managing network routers: MikroTik RouterOS, OpenWrt and its derivatives, OPNsense, pfSense, VyOS, Keenetic and Netcraze, plain Linux hosts over SSH, and devices that answer SNMP, UPnP or TR-064. This policy covers the desktop apps for Windows, macOS and Linux, the Android and iOS apps, and the browser gateway that the desktop app or the separate gateway server provides.

## What the app does not do

The app has no account with the developer, no analytics, no crash reporting, no advertising and no tracking. It does not send anything to the developer or to any server of the developer. The developer has no way to see which routers you manage or what you do with them.

## Connections the app makes

Apart from the check for a new version and the list of changes of a RouterOS version, both described below, the app connects only to the addresses you enter or pick, and only by the protocol you choose for each of them: the RouterOS API, API-SSL and REST, SSH and SFTP, the ubus interface of OpenWrt over SSH or HTTP, the REST APIs of OPNsense, pfSense and VyOS, RCI of Keenetic, SNMP v2c, UPnP and TR-064. When you route a connection through another router ("Via"), the app opens an SSH tunnel through that router. Your login, the commands you run and the configuration you read or change travel between your device and your routers only.

Some protocols the app offers are not encrypted (the RouterOS API on port 8728, REST and ubus over HTTP, RCI over HTTP, SNMP v2c, UPnP). The app marks them in the connection window and in the main window. Over such a protocol the password and every value can be read by anyone on the network path.

Discovery of routers on the local network listens for MikroTik Neighbor Discovery (MNDP) broadcasts. The subnet scan, which you start yourself, opens a TCP connection to the ports of the supported protocols on each address of the subnet you choose and closes it at once, without a login.

## Check for a new version

The desktop and Android apps ask GitHub (`api.github.com`) for the list of releases of the project when they start. The request carries no data about you or your routers; GitHub sees your IP address and the version of the app in the `User-Agent` header, as any web server would, and handles them under its own privacy policy. If a newer release exists, the toolbar shows a button that opens its page in your browser; with no answer, nothing is shown. The app downloads and installs nothing by itself. The iOS app, whose updates come through the store, and the browser version make no such request. The check is switched off in Settings under "Check for new versions of NetworkHelm"; with the setting off the app makes no request at all at start.

The notice about a newer RouterOS comes from the router: the app reads the update state that the router keeps (`/system/package/update`) over the connection you opened. The notice does not ask MikroTik's servers.

## List of changes of a RouterOS version

The window "Check For Updates" shows the list of changes of the version the router offers. Over the WinBox protocol (port 8291), which the desktop and Android apps open beside the API when the port answers, the list comes from the router itself and nothing leaves the local connection. When the app has no such channel to the router, the desktop and Android apps ask the manufacturer's server for the text: one HTTPS GET to `https://upgrade.mikrotik.com/routeros/<version>/CHANGELOG`, where `<version>` is the latest version the router names for its update channel (for example `7.26beta5`). The request goes out only while the window is open; the answer is kept in memory only, so a version that was answered is asked for once during one run of the app. It carries the version of the app in the `User-Agent` header (`NetworkHelm/<version>`) and the address the request comes from, as any request to the network does; nothing about the router goes with it, neither its address, name, serial number nor login. A redirect to another host is not followed, an answer larger than 256 KiB is dropped, and when the server does not answer the box stays empty and nothing is reported. The iOS app and the browser version make no such request.

## Network tools

The tools of the "Network tools" window send requests only when you press Start, and only for what you typed. Ping, traceroute, the port scanner, the LAN scanner and Wake-on-LAN talk to the addresses you enter or to the addresses of your own network. The DNS lookup asks the resolver you pick (Cloudflare, Google, Quad9, Yandex or one you type). Whois over port 43 asks `whois.iana.org` and the server it names; RDAP asks `data.iana.org` for the list of servers and then the RDAP server of the registry (`rdap.org` when the list names none). IP info asks the DNS service of Team Cymru (`asn.cymru.com`), the RDAP server of the registry, `ipwho.is` (`ipapi.co` when that fails) and, for your own address, `api.ipify.org`. The speed test talks to `speed.cloudflare.com` or to the server you type and moves data for about eight seconds in each direction, which on a fast line is hundreds of megabytes. These hosts see your IP address and what you typed, and where HTTP is used the `User-Agent` header with the name and version of the app; they handle them under their own policies. Nothing goes to the developer. The iOS app and the browser version open fewer of these connections, because they can open fewer kinds.

## Data stored on your device

- **Saved routers:** address, user name, protocol, group, comment and the router to connect through. They are stored in the app's own data folder.
- **Passwords and SSH keys** you choose to remember use the operating system's secure storage when available: Windows Data Protection (DPAPI), the macOS or iOS Keychain, the Secret Service on Linux, or the Android Keystore. On desktop systems where the system storage is unavailable, the storage library falls back to software encryption in the app's data folder; this does not provide the same protection as system-backed storage.
- **Fingerprints** of router certificates and SSH host keys you accepted, so that a changed certificate or key is reported.
- **Settings and layouts:** window positions, table columns and sorting, the theme, terminal snippets and the like.
- **Cached menu descriptions** of RouterOS versions the app has seen, to open windows faster.
- **The journal of edits** of the current session, kept in memory until the app closes.

An exported address book is a file you save yourself. If you protect it with a password, its contents are encrypted with AES-256-GCM under a key derived from that password with PBKDF2.

On Android the system may include the app's data folder in your device backup, if you have backups turned on. Remembered passwords and keys are not usable from such a backup: their encryption key stays in the Android Keystore of the device.

Removing the app removes this data, except files you exported yourself and, on some systems, entries the operating system keeps in its secure storage until you delete them there.

## The app in a browser

When you turn on access from a browser in the desktop app, or run the gateway server, the gateway stores on the computer it runs on: its own account (a user name, a value derived from the password to check it, and the secret of an authenticator app if you set one up), a log of logins with time and address, its TLS certificate, and the routers, passwords and fingerprints you save while working in the browser, the passwords encrypted with a key derived from the gateway password. The browser keeps only its session cookie. The gateway reaches your routers the same way the desktop app does and sends nothing elsewhere.

When an OpenWrt router serves the web app itself, the app talks only to that router. The saved routers and the settings stay in the local storage of that browser; passwords are kept only until the page is closed.

The page without a gateway, published on GitHub Pages or another static host, works the same way: the browser reaches your routers by itself and the page sends nothing to the developer. The host sees the requests for the files of the page, as any host does, under its own privacy policy. The six demo routers in the list of the page are generated inside it and exist nowhere else.

## Permissions

- **Android:** internet and local network access, to connect to routers; Wi-Fi multicast, to receive MNDP announcements.
- **iOS and macOS:** local network access, to connect to routers and receive their announcements.
- **Files:** the app reads and writes only the files you pick: uploads and downloads of router files, exported configurations and address books.

## Children

The app is a tool for administering network equipment and is not directed at children.

## Changes

A change to this policy is published in this file in the repository, with a new date at the top.

**Developer:** vasmarfas
**Contact:** https://github.com/vasmarfas/networkhelm/issues
