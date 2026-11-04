NetworkHelm gateway

The gateway opens NetworkHelm in a browser: it serves the web app over HTTPS and connects to the routers for the page.
It carries its own Java, nothing else is to be installed.

Windows: run networkhelm-gateway.bat. The window shows the address and the password of admin once; closing it stops the gateway.
Linux:   run ./networkhelm-gateway.sh, or install networkhelm-gateway.service (see the comments in it).

Then open https://<address of this computer>:8443 and log in as admin. The certificate is made at the first start and is
self-signed: the browser asks once, and the SHA-256 printed at every start is what its details should show.

Options after the command: --port 8443, --host 0.0.0.0, --public (answer any address, the code of an authenticator app is
then always asked), --code (ask the code of an authenticator app). NETWORKHELM_GATEWAY_PASSWORD sets the password of the
first start, NETWORKHELM_GATEWAY_HOME the folder of the account, the certificate and the saved routers
(~/.networkhelm-gateway by default).

The password is changed from the console, the program ends after it and a gateway that runs takes the change at its next
request, ending its sessions:
  --set-password    a new password, typed twice (or in NETWORKHELM_GATEWAY_PASSWORD); the saved passwords of the routers
                    pass to it when the current one is typed (or in NETWORKHELM_GATEWAY_CURRENT_PASSWORD), Enter if it is lost
  --reset-password  a password made up and shown once; the saved passwords of the routers are put aside as secrets.json.old
  --new-code        a new secret of the authenticator app, as when its phone is lost

The routers reached over port 8291 and by MAC address, and the neighbors found by MNDP, are those of the network this
computer is in.
