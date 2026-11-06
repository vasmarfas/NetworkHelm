# MIB dictionary

`build.py` writes `core/schema/snmp-mibs.tsv`, the dictionary the SNMP session uses to name what an agent has, to show its values and to know what may be written. Gradle turns the file into Kotlin (`bundleMibs` in `core/build.gradle.kts`), so after a rebuild of the file nothing else needs to change.

The modules come from two places:

- the texts of the RFCs, `https://www.rfc-editor.org/rfc/rfcNNNN.txt` for 1213, 1573 (only IANAifType-MIB of 1994), 2578, 2579, 2790, 2863, 3411, 3412, 3413, 3414, 3415, 3418, 3433, 3584, 3621, 3635, 4001, 4022, 4113, 4188, 4292, 4293, 4363 and 6933, saved under their own names (`rfc2863.txt`) in one folder;
- the MIB files of net-snmp 5.9 (`/usr/share/snmp/mibs`, package `libsnmp-base` on Debian and Ubuntu): NET-SNMP-*, UCD-* and LM-SENSORS-MIB.

```
python tools/snmp-mibs/build.py <folder of RFC texts> <folder of net-snmp MIBs>
```

The script cuts the modules out of the RFC pages, parses OBJECT-TYPE, OBJECT IDENTIFIER, OBJECT-IDENTITY, MODULE-IDENTITY and TEXTUAL-CONVENTION and writes one line per object, sorted by OID: OID, name, module, kind (`group`, `scalar`, `table`, `entry`, `column`, `vendor`), base type, textual convention, display hint, enumeration, access, INDEX of an entry (`*` marks IMPLIED, AUGMENTS is resolved to the index of the entry it extends) and units. Descriptions stay out. The `vendor` lines are the IANA enterprise numbers of common makers, kept by hand in `VENDORS`. Three textual conventions of modules that are not read (HCNUM-TC, RMON2-MIB, IANA-RTPROTO-MIB) are given by their base type in `OUTSIDE`.

The OIDs, access rights, indexes and enumerations of the result were compared with `snmptranslate -Td` of net-snmp 5.9.4 over the same modules; they differ only where net-snmp shows the older RFC1213-MIB text of an object that a newer module redefines.
