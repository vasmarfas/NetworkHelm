package com.vasmarfas.networkhelm.nettools

// answers of the shape registries give, with made-up names and documentation addresses
object RdapSamples {
    val domain = """
        {
          "objectClassName": "domain",
          "handle": "2336799_DOMAIN_COM-VRSN",
          "ldhName": "EXAMPLE.COM",
          "status": ["client delete prohibited", "client transfer prohibited", "client update prohibited"],
          "entities": [
            {
              "objectClassName": "entity",
              "handle": "9999",
              "roles": ["registrar"],
              "publicIds": [{"type": "IANA Registrar ID", "identifier": "9999"}],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Registrar, Inc."]]],
              "entities": [
                {
                  "objectClassName": "entity",
                  "roles": ["abuse"],
                  "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", ""], ["tel", {"type": "voice"}, "uri", "tel:+1.5555550100"], ["email", {}, "text", "abuse@registrar.test"]]]
                }
              ]
            },
            {
              "objectClassName": "entity",
              "roles": ["registrant"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "REDACTED FOR PRIVACY"], ["org", {}, "text", ["REDACTED FOR PRIVACY"]]]]
            }
          ],
          "events": [
            {"eventAction": "registration", "eventDate": "1995-08-14T04:00:00Z"},
            {"eventAction": "expiration", "eventDate": "2026-08-13T04:00:00Z"},
            {"eventAction": "last changed", "eventDate": "2025-08-14T07:01:44Z"},
            {"eventAction": "last update of RDAP database", "eventDate": "2026-11-06T09:00:00Z"}
          ],
          "secureDNS": {"delegationSigned": true},
          "nameservers": [
            {"objectClassName": "nameserver", "ldhName": "A.IANA-SERVERS.NET"},
            {"objectClassName": "nameserver", "ldhName": "B.IANA-SERVERS.NET"}
          ],
          "port43": "whois.registry.test"
        }
    """.trimIndent()

    val network = """
        {
          "objectClassName": "ip network",
          "handle": "193.0.0.0 - 193.0.7.255",
          "startAddress": "193.0.0.0",
          "endAddress": "193.0.7.255",
          "ipVersion": "v4",
          "name": "EXAMPLE-NET",
          "type": "ASSIGNED PA",
          "country": "NL",
          "parentHandle": "193.0.0.0 - 193.255.255.255",
          "status": ["active"],
          "entities": [
            {
              "objectClassName": "entity",
              "handle": "ORG-EX1-RIPE",
              "roles": ["registrant"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Example Network Coordination Centre"], ["kind", {}, "text", "org"], ["adr", {"label": "Example Street 1\nExample City\nEXAMPLELAND"}, "text", null]]]
            },
            {
              "objectClassName": "entity",
              "handle": "OPS4-EX",
              "roles": ["technical", "administrative"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Operations Department"], ["email", {}, "text", "ops@example.test"]]]
            },
            {
              "objectClassName": "entity",
              "handle": "ABUSE-EX",
              "roles": ["abuse"],
              "vcardArray": ["vcard", [["version", {}, "text", "4.0"], ["fn", {}, "text", "Abuse Contact"], ["email", {}, "text", "abuse@example.test"], ["tel", {}, "uri", "tel:+31.20.5550100"]]]
            }
          ],
          "events": [
            {"eventAction": "registration", "eventDate": "2004-03-17T11:19:12Z"},
            {"eventAction": "last changed", "eventDate": "2018-11-23T10:30:36Z"}
          ],
          "remarks": [{"description": ["Example network", "City, Country"]}],
          "port43": "whois.ripe.net",
          "cidr0_cidrs": [{"v4prefix": "193.0.0.0", "length": 21}]
        }
    """.trimIndent()

    val autnum = """
        {
          "objectClassName": "autnum",
          "handle": "AS64496",
          "startAutnum": 64496,
          "endAutnum": 64496,
          "name": "EXAMPLE-AS",
          "type": "DIRECT ALLOCATION",
          "country": "ZZ",
          "status": ["active"],
          "events": [{"eventAction": "last changed", "eventDate": "2020-01-01T00:00:00Z"}]
        }
    """.trimIndent()

    val dnsFile = """{"description":"RDAP Bootstrap file for Domain Name System registrations","publication":"2026-10-01T00:00:00Z","services":[[["com","net"],["https://rdap.registry.test/com/v1/"]],[["org"],["http://plain.test/","https://rdap.org.test/"]],[["xn--p1ai"],["https://rdap.cctld.test/"]],[["dev"],["http://plain.test/"]]],"version":"1.0"}"""

    val ipv4File = """{"services":[[["1.0.0.0/8","2.0.0.0/8"],["https://rdap.apnic.test/"]],[["8.0.0.0/8","9.0.0.0/8"],["https://rdap.arin.test/registry/"]],[["193.0.0.0/8"],["https://rdap.ripe.test/"]]],"version":"1.0"}"""

    val ipv6File = """{"services":[[["2001:0200::/23","2001:0c00::/23"],["https://rdap.apnic.test/"]],[["2001:0400::/23"],["https://rdap.arin.test/registry/"]],[["2a00::/12"],["https://rdap.ripe.test/"]]],"version":"1.0"}"""

    val asnFile = """{"services":[[["64496-64511"],["https://rdap.documentation.test/"]],[["15169","13335-13336"],["https://rdap.arin.test/registry/"]],[["4200000000-4294967294"],["https://rdap.private.test/"]]],"version":"1.0"}"""

    // the four IANA files under their addresses
    fun files(): Map<String, String> = mapOf(
        "https://data.iana.org/rdap/dns.json" to dnsFile,
        "https://data.iana.org/rdap/ipv4.json" to ipv4File,
        "https://data.iana.org/rdap/ipv6.json" to ipv6File,
        "https://data.iana.org/rdap/asn.json" to asnFile,
    )
}
