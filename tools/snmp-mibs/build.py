"""Builds core/schema/snmp-mibs.tsv, the dictionary of MIB objects the SNMP session uses to name and write what an agent has.

The modules come from the texts of the RFCs (www.rfc-editor.org/rfc/rfcNNNN.txt) and from the MIB files of net-snmp
(/usr/share/snmp/mibs of the net-snmp package). Each RFC is cut into its modules, the modules are parsed, and every OBJECT-TYPE,
OBJECT IDENTIFIER, OBJECT-IDENTITY and MODULE-IDENTITY goes into one line: OID, name, module, kind, base type, textual
convention, display hint, enumeration, access, index of an entry and units. Descriptions stay out.

Usage: python tools/snmp-mibs/build.py DIR [DIR ...]
"""
import re
import sys
from pathlib import Path

OUTPUT = Path(__file__).resolve().parents[2] / "core" / "schema" / "snmp-mibs.tsv"

# later modules of the same name win, so a list in RFC order keeps the newest text
MODULES = [
    "RFC1213-MIB", "IANAifType-MIB", "SNMPv2-SMI", "SNMPv2-TC", "SNMPv2-MIB", "HOST-RESOURCES-MIB", "HOST-RESOURCES-TYPES", "IF-MIB",
    "SNMP-FRAMEWORK-MIB", "SNMP-MPD-MIB", "SNMP-TARGET-MIB", "SNMP-NOTIFICATION-MIB", "SNMP-PROXY-MIB", "SNMP-USER-BASED-SM-MIB",
    "SNMP-VIEW-BASED-ACM-MIB", "ENTITY-SENSOR-MIB", "SNMP-COMMUNITY-MIB", "POWER-ETHERNET-MIB", "EtherLike-MIB", "INET-ADDRESS-MIB",
    "TCP-MIB", "UDP-MIB", "BRIDGE-MIB", "IP-FORWARD-MIB", "IP-MIB", "P-BRIDGE-MIB", "Q-BRIDGE-MIB", "ENTITY-MIB", "IANA-ENTITY-MIB",
    "UUID-TC-MIB", "NET-SNMP-MIB", "NET-SNMP-TC", "NET-SNMP-AGENT-MIB", "NET-SNMP-EXTEND-MIB", "NET-SNMP-VACM-MIB",
    "UCD-SNMP-MIB", "UCD-DISKIO-MIB", "UCD-DLMOD-MIB", "UCD-IPFWACC-MIB", "LM-SENSORS-MIB",
]

# IANA private enterprise numbers of common makers of network gear
VENDORS = {
    9: "Cisco", 11: "HP", 43: "3Com", 171: "D-Link", 674: "Dell", 890: "ZyXEL", 1916: "Extreme Networks", 1991: "Brocade",
    2011: "Huawei", 2021: "UCD-SNMP", 2620: "Check Point", 2636: "Juniper", 3375: "F5", 3902: "ZTE", 3955: "Linksys", 4413: "Broadcom",
    4526: "Netgear", 6027: "Force10", 6486: "Alcatel-Lucent", 6527: "Nokia", 8072: "Net-SNMP", 8741: "SonicWall", 10002: "Frogfoot",
    11863: "TP-Link", 12356: "Fortinet", 14823: "Aruba", 14988: "MikroTik", 17713: "Cambium", 25053: "Ruckus", 25461: "Palo Alto Networks",
    25506: "H3C", 30065: "Arista", 40418: "SNR", 41112: "Ubiquiti",
}

# textual conventions of modules that stay out of the dictionary, by their syntax
OUTSIDE = {"CounterBasedGauge64": "Counter64", "TimeFilter": "TimeTicks", "IANAipRouteProtocol": "INTEGER"}

BASES = {
    "INTEGER": "Integer32", "Integer32": "Integer32", "Unsigned32": "Unsigned32", "Gauge32": "Gauge32", "Gauge": "Gauge32",
    "Counter32": "Counter32", "Counter": "Counter32", "Counter64": "Counter64", "TimeTicks": "TimeTicks", "IpAddress": "IpAddress",
    "NetworkAddress": "IpAddress", "Opaque": "Opaque", "OCTET": "OctetString", "OBJECT": "ObjectIdentifier", "BITS": "Bits",
}

MACROS = {
    "MODULE-IDENTITY", "OBJECT-IDENTITY", "OBJECT-TYPE", "NOTIFICATION-TYPE", "TRAP-TYPE", "OBJECT-GROUP", "NOTIFICATION-GROUP",
    "MODULE-COMPLIANCE", "AGENT-CAPABILITIES",
}

TOKEN = re.compile(r'"[^"]*"|::=|\.\.|[{}()\[\],;|]|-?\d+|\'[0-9A-Fa-f]*\'[HhBb]|[A-Za-z][\w-]*')


def strip_pages(text):
    lines = []
    for line in text.replace("\f", "\n").splitlines():
        if re.match(r"^RFC \d+ .*\d{4}$", line) or re.search(r"\[Page \d+\]$", line):
            continue
        lines.append(line)
    return "\n".join(lines)


def without_comments(text):
    kept, at, quoted = [], 0, False
    while at < len(text):
        if text[at] == '"':
            quoted = not quoted
        elif not quoted and text.startswith("--", at):
            ends = [end for end in (text.find("--", at + 2), text.find("\n", at + 2)) if end >= 0]
            end = min(ends) if ends else len(text)
            at = end + 2 if text.startswith("--", end) else end
            continue
        kept.append(text[at])
        at += 1
    return "".join(kept)


def modules_of(text):
    lines = text.splitlines()
    found, at = {}, 0
    while at < len(lines):
        start = re.match(r"^\s*([A-Za-z][\w-]*)\s+DEFINITIONS\s*::=\s*BEGIN\s*(--.*)?$", lines[at])
        at += 1
        if start is None:
            continue
        depth, body = 1, []
        while at < len(lines) and depth:
            line = lines[at]
            at += 1
            bare = line.split("--")[0].strip()
            if re.search(r"\bMACRO\s*::=", bare):
                depth += 1
            elif bare == "END":
                depth -= 1
            body.append(line)
        found[start.group(1)] = TOKEN.findall(without_comments("\n".join(body[:-1])))
    return found


class Module:
    def __init__(self, name, tokens):
        self.name = name
        self.tokens = tokens
        self.at = 0
        self.imports = {}
        self.objects = []
        self.conventions = {}

    def peek(self, ahead=0):
        return self.tokens[self.at + ahead] if self.at + ahead < len(self.tokens) else None

    def take(self, expected=None):
        token = self.tokens[self.at]
        if expected is not None and token != expected:
            raise ValueError(f"{self.name}: {expected} expected, {token} found near {' '.join(self.tokens[self.at - 8:self.at + 8])}")
        self.at += 1
        return token

    def balanced(self, opening, closing):
        self.take(opening)
        taken, depth = [], 1
        while depth:
            token = self.take()
            depth += (token == opening) - (token == closing)
            if depth:
                taken.append(token)
        return taken

    def parse(self):
        if self.peek() == "EXPORTS":
            while self.take() != ";":
                pass
        if self.peek() == "IMPORTS":
            self.take()
            names = []
            while self.peek() != ";":
                token = self.take()
                if token == "FROM":
                    source = self.take()
                    for name in names:
                        self.imports[name] = source
                    names = []
                elif token != ",":
                    names.append(token)
            self.take(";")
        while self.at < len(self.tokens):
            self.statement()

    def statement(self):
        name = self.take()
        token = self.take()
        if token == "MACRO":
            self.take("::=")
            self.take("BEGIN")
            while self.take() != "END":
                pass
        elif token == "OBJECT":
            self.take("IDENTIFIER")
            self.take("::=")
            self.objects.append({"name": name, "kind": "group", "value": self.oid_value()})
        elif token in MACROS:
            self.macro(name, token)
        elif token == "::=":
            self.assignment(name)
        else:
            raise ValueError(f"{self.name}: cannot read the statement {name} {token}")

    def oid_value(self):
        parts = []
        for token in self.balanced("{", "}"):
            if token in "()":
                continue
            if token.isdigit():
                if parts and parts[-1][1] is None and parts[-1][0] is not None and len(parts) > 1:
                    parts[-1] = (parts[-1][0], int(token))
                else:
                    parts.append((None, int(token)))
            else:
                parts.append((token, None))
        return parts

    def macro(self, name, macro):
        clauses = {}
        while self.peek() != "::=":
            token = self.take()
            if token == "SYNTAX" and macro == "OBJECT-TYPE":
                clauses["syntax"] = self.syntax()
            elif token in ("UNITS", "DESCRIPTION", "REFERENCE", "DISPLAY-HINT") and self.peek().startswith('"'):
                clauses[token] = self.take()[1:-1]
            elif token in ("MAX-ACCESS", "ACCESS") and macro == "OBJECT-TYPE":
                clauses["access"] = self.take()
            elif token in ("INDEX", "AUGMENTS"):
                clauses[token] = [t for t in self.balanced("{", "}") if t != ","]
            elif token in ("DEFVAL", "OBJECTS", "NOTIFICATIONS", "VARIABLES") and self.peek() == "{":
                self.balanced("{", "}")
        self.take("::=")
        if self.peek() != "{":
            self.take()
            return
        value = self.oid_value()
        if macro not in ("OBJECT-TYPE", "OBJECT-IDENTITY", "MODULE-IDENTITY"):
            return
        self.objects.append({"name": name, "kind": "object" if macro == "OBJECT-TYPE" else "group", "value": value, **clauses})

    def syntax(self):
        token = self.take()
        if token == "SEQUENCE":
            if self.peek() == "OF":
                self.take()
                return {"table": self.take()}
            self.balanced("{", "}")
            return {"sequence": True}
        if token == "CHOICE":
            self.balanced("{", "}")
            return {"sequence": True}
        if token == "[":
            while self.take() != "]":
                pass
            if self.peek() == "IMPLICIT":
                self.take()
            return self.syntax()
        if token == "OCTET":
            self.take("STRING")
        elif token == "OBJECT":
            self.take("IDENTIFIER")
        result = {"type": token}
        if self.peek() == "{":
            result["enums"] = enumeration(self.balanced("{", "}"))
        elif self.peek() == "(":
            self.balanced("(", ")")
        return result

    def assignment(self, name):
        if self.peek() == "TEXTUAL-CONVENTION":
            self.take()
            hint = None
            while self.peek() != "SYNTAX":
                token = self.take()
                if token == "DISPLAY-HINT":
                    hint = self.take()[1:-1]
            self.take("SYNTAX")
            self.conventions[name] = {**self.syntax(), "hint": hint, "explicit": True}
        else:
            syntax = self.syntax()
            if "sequence" not in syntax and "table" not in syntax:
                self.conventions[name] = {**syntax, "hint": None, "explicit": False}


def enumeration(tokens):
    named = []
    for at in range(len(tokens) - 3):
        if tokens[at + 1] == "(" and tokens[at + 3] == ")":
            named.append(f"{tokens[at]}({tokens[at + 2]})")
    return ",".join(named)


def main(directories):
    texts = {}
    for directory in directories:
        for path in sorted(Path(directory).iterdir(), key=lambda p: (int(m.group(1)) if (m := re.match(r"rfc(\d+)\.txt$", p.name)) else 0, p.name)):
            if path.suffix != ".txt":
                continue
            text = path.read_text(encoding="latin-1")
            if path.name.startswith("rfc"):
                text = strip_pages(text)
            for name, tokens in modules_of(text).items():
                if name in MODULES:
                    texts[name] = tokens
    missing = [name for name in MODULES if name not in texts]
    if missing:
        sys.exit(f"modules not found: {', '.join(missing)}")
    modules = []
    for name in MODULES:
        module = Module(name, texts[name])
        module.parse()
        modules.append(module)

    conventions = {}
    for module in modules:
        for name, convention in module.conventions.items():
            if name not in conventions or convention["explicit"]:
                conventions[name] = {**convention, "module": module.name}

    defined = {"iso": [1], "ccitt": [0], "joint-iso-ccitt": [2]}
    pending = [(module, item) for module in modules for item in module.objects]
    while pending:
        rest = []
        for module, item in pending:
            arcs = []
            for name, number in item["value"]:
                if number is not None:
                    arcs.append(number)
                elif name in defined and not arcs:
                    arcs.extend(defined[name])
                else:
                    break
            else:
                item["oid"] = arcs
                if item["name"] not in defined or module.name != "RFC1213-MIB":
                    defined[item["name"]] = arcs
                continue
            rest.append((module, item))
        if len(rest) == len(pending):
            sys.exit("unresolved: " + ", ".join(f"{m.name}::{i['name']}" for m, i in rest))
        pending = rest

    rows = {}
    entries = {}
    for module in modules:
        for item in module.objects:
            # RFC1213-MIB goes first, so a newer module that names the same object takes its line
            rows[".".join(map(str, item["oid"]))] = (module, item)
            if "INDEX" in item or "AUGMENTS" in item:
                entries[item["name"]] = item

    def resolve(syntax):
        tc, hint, enums, seen = None, None, syntax.get("enums"), set()
        kind = syntax["type"]
        while kind not in BASES:
            convention = conventions.get(kind) or ({"type": OUTSIDE[kind], "hint": None} if kind in OUTSIDE else None)
            if convention is None or kind in seen:
                sys.exit(f"unknown type {kind}")
            seen.add(kind)
            tc = tc or kind
            hint = hint or convention["hint"]
            enums = enums or convention.get("enums")
            kind = convention["type"]
        return BASES[kind], tc, hint, enums

    def index_of(entry, depth=0):
        if "INDEX" in entry:
            return entry["INDEX"]
        base = entries.get(entry["AUGMENTS"][0])
        if base is None or depth > 3:
            sys.exit(f"{entry['name']} augments an unknown entry")
        return index_of(base, depth + 1)

    lines = ["# oid\tname\tmodule\tkind\ttype\ttc\thint\tenums\taccess\tindex\tunits"]
    for oid in sorted(rows, key=lambda o: [int(a) for a in o.split(".")]):
        module, item = rows[oid]
        kind, base, tc, hint, enums, access, index = item["kind"], "", "", "", "", item.get("access", ""), ""
        syntax = item.get("syntax")
        if syntax is not None:
            parent = ".".join(oid.split(".")[:-1])
            parent_item = rows.get(parent, (None, {}))[1]
            if "table" in syntax:
                kind = "table"
            elif "INDEX" in item or "AUGMENTS" in item:
                kind = "entry"
                index = ",".join(index_of(item)).replace("IMPLIED,", "*")
            elif "INDEX" in parent_item or "AUGMENTS" in parent_item:
                kind = "column"
            else:
                kind = "scalar"
            if kind in ("scalar", "column"):
                base, tc, hint, enums = resolve(syntax)
                tc, hint, enums = tc or "", hint or "", enums or ""
        lines.append("\t".join([oid, item["name"], module.name, kind, base, tc, hint, enums, access, index, item.get("UNITS", "")]))
    for number, vendor in sorted(VENDORS.items()):
        lines.append("\t".join([f"1.3.6.1.4.1.{number}", vendor, "", "vendor", "", "", "", "", "", "", ""]))
    OUTPUT.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(lines) - 1} lines, {OUTPUT.stat().st_size} bytes in {OUTPUT}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
