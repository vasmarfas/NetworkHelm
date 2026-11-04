# Terms of Use

Last updated: 23 October 2026

These terms cover the NetworkHelm apps for Windows, macOS, Linux, Android and iOS, the web app and the browser gateway. By installing or using the app you agree to them. The source code is public under the license in [LICENSE](LICENSE), explained in [NOTICE.md](NOTICE.md); where these terms and the license disagree, the license controls for the code, and these terms for the use of the published builds.

## What the app is

NetworkHelm reads and changes the configuration of network devices you connect it to: routers on MikroTik RouterOS, OpenWrt and its derivatives, OPNsense, pfSense, VyOS, Keenetic and Netcraze, Linux hosts over SSH, and devices that answer SNMP, UPnP or TR-064. It runs commands in their terminals, transfers files to and from them, opens SSH tunnels through them, and, if you turn it on, serves itself to a browser through its gateway.

## Acceptable use

- Use the app only with devices you own or are authorized to administer. Connecting to, scanning or changing devices without permission may be unlawful where you are.
- The subnet scan and the discovery of neighbours are for networks you administer.
- Do not use the app, its gateway or its tunnels to reach, attack or overload systems of other people.
- The license allows noncommercial use. Using the app in the operations of a business, including an administrator using it at work, is commercial use and needs a separate agreement with the developer; ask through the contact below.

## Forks and modified builds

You may change your own copy and fork the repository to prepare a pull request. You agree not to distribute a modified version to other people in any other way: not through an app store, a download page or a package repository, and not under the name, icon or application ID of NetworkHelm. A change worth sharing goes back to the project as a contribution.

## Your configuration is your responsibility

A change to a router can cut you off from it or from the network: a firewall rule, an address, a route, a disabled interface. Safe Mode and the rollback the app offers on some systems (Safe Mode of RouterOS, the rollback of `uci apply` on OpenWrt, commit-confirm on VyOS, the rollback of OPNsense) reduce that risk but do not remove it, and not every system has one. Writes to pfSense, a reboot, a firmware upgrade or a package installation cannot be taken back by the app. Keep a backup of the configuration before larger changes.

Unencrypted protocols (the RouterOS API on port 8728, HTTP, SNMP v2c, UPnP) send the password and the data in the clear. The app marks them; using them is your choice.

## No warranty

The app is provided as is, without warranty of any kind, express or implied, including fitness for a particular purpose. Support for a platform does not mean every version, model or configuration of it was tested: several platforms were written from their public documentation and checked against simulated answers only, as the changelog and the documentation say.

## Limitation of liability

To the extent the law allows, the developer is not liable for any damage that results from using the app or from being unable to use it, including lost access to devices, interrupted networks, lost configuration or data, and costs of recovery.

## Trademarks

MikroTik, RouterOS, WinBox and RouterBOARD are trademarks of SIA Mikrotīkls. OpenWrt is a registered trademark of Software Freedom Conservancy. OPNsense, pfSense, VyOS, Keenetic, Netcraze, Teltonika, GL.iNet, FRITZ!Box and other names belong to their owners. NetworkHelm is not affiliated with, endorsed by or sponsored by any of them; the names appear only to say which devices the app works with.

## Changes

A change to these terms is published in this file in the repository, with a new date at the top.

**Developer:** vasmarfas
**Contact:** https://github.com/vasmarfas/networkhelm/issues
