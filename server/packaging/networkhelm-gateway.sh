#!/bin/sh
# The gateway of NetworkHelm. The password of the first start is printed once: in the terminal, or in the journal under systemd.
here=$(dirname "$(readlink -f "$0")")
exec "$here/runtime/bin/java" $JAVA_OPTS -jar "$here/networkhelm-gateway.jar" "$@"
