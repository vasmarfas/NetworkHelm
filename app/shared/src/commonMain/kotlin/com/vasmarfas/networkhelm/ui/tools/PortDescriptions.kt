package com.vasmarfas.networkhelm.ui.tools

import org.jetbrains.compose.resources.StringResource
import networkhelm.app.shared.generated.resources.Res
import networkhelm.app.shared.generated.resources.tools_b_port_10000
import networkhelm.app.shared.generated.resources.tools_b_port_10050
import networkhelm.app.shared.generated.resources.tools_b_port_10051
import networkhelm.app.shared.generated.resources.tools_b_port_1080
import networkhelm.app.shared.generated.resources.tools_b_port_110
import networkhelm.app.shared.generated.resources.tools_b_port_111
import networkhelm.app.shared.generated.resources.tools_b_port_11211
import networkhelm.app.shared.generated.resources.tools_b_port_119
import networkhelm.app.shared.generated.resources.tools_b_port_1194
import networkhelm.app.shared.generated.resources.tools_b_port_123
import networkhelm.app.shared.generated.resources.tools_b_port_135
import networkhelm.app.shared.generated.resources.tools_b_port_137
import networkhelm.app.shared.generated.resources.tools_b_port_138
import networkhelm.app.shared.generated.resources.tools_b_port_139
import networkhelm.app.shared.generated.resources.tools_b_port_143
import networkhelm.app.shared.generated.resources.tools_b_port_1433
import networkhelm.app.shared.generated.resources.tools_b_port_1521
import networkhelm.app.shared.generated.resources.tools_b_port_161
import networkhelm.app.shared.generated.resources.tools_b_port_162
import networkhelm.app.shared.generated.resources.tools_b_port_1701
import networkhelm.app.shared.generated.resources.tools_b_port_1723
import networkhelm.app.shared.generated.resources.tools_b_port_179
import networkhelm.app.shared.generated.resources.tools_b_port_1812
import networkhelm.app.shared.generated.resources.tools_b_port_1813
import networkhelm.app.shared.generated.resources.tools_b_port_1883
import networkhelm.app.shared.generated.resources.tools_b_port_1900
import networkhelm.app.shared.generated.resources.tools_b_port_194
import networkhelm.app.shared.generated.resources.tools_b_port_20
import networkhelm.app.shared.generated.resources.tools_b_port_2049
import networkhelm.app.shared.generated.resources.tools_b_port_21
import networkhelm.app.shared.generated.resources.tools_b_port_2181
import networkhelm.app.shared.generated.resources.tools_b_port_22
import networkhelm.app.shared.generated.resources.tools_b_port_23
import networkhelm.app.shared.generated.resources.tools_b_port_2375
import networkhelm.app.shared.generated.resources.tools_b_port_2376
import networkhelm.app.shared.generated.resources.tools_b_port_25
import networkhelm.app.shared.generated.resources.tools_b_port_25565
import networkhelm.app.shared.generated.resources.tools_b_port_27017
import networkhelm.app.shared.generated.resources.tools_b_port_3000
import networkhelm.app.shared.generated.resources.tools_b_port_3128
import networkhelm.app.shared.generated.resources.tools_b_port_32400
import networkhelm.app.shared.generated.resources.tools_b_port_3306
import networkhelm.app.shared.generated.resources.tools_b_port_3389
import networkhelm.app.shared.generated.resources.tools_b_port_3478
import networkhelm.app.shared.generated.resources.tools_b_port_389
import networkhelm.app.shared.generated.resources.tools_b_port_443
import networkhelm.app.shared.generated.resources.tools_b_port_4443
import networkhelm.app.shared.generated.resources.tools_b_port_445
import networkhelm.app.shared.generated.resources.tools_b_port_4500
import networkhelm.app.shared.generated.resources.tools_b_port_465
import networkhelm.app.shared.generated.resources.tools_b_port_500
import networkhelm.app.shared.generated.resources.tools_b_port_5000
import networkhelm.app.shared.generated.resources.tools_b_port_5001
import networkhelm.app.shared.generated.resources.tools_b_port_5060
import networkhelm.app.shared.generated.resources.tools_b_port_5061
import networkhelm.app.shared.generated.resources.tools_b_port_514
import networkhelm.app.shared.generated.resources.tools_b_port_515
import networkhelm.app.shared.generated.resources.tools_b_port_51820
import networkhelm.app.shared.generated.resources.tools_b_port_520
import networkhelm.app.shared.generated.resources.tools_b_port_5201
import networkhelm.app.shared.generated.resources.tools_b_port_5222
import networkhelm.app.shared.generated.resources.tools_b_port_53
import networkhelm.app.shared.generated.resources.tools_b_port_5353
import networkhelm.app.shared.generated.resources.tools_b_port_5432
import networkhelm.app.shared.generated.resources.tools_b_port_546
import networkhelm.app.shared.generated.resources.tools_b_port_547
import networkhelm.app.shared.generated.resources.tools_b_port_548
import networkhelm.app.shared.generated.resources.tools_b_port_554
import networkhelm.app.shared.generated.resources.tools_b_port_5683
import networkhelm.app.shared.generated.resources.tools_b_port_587
import networkhelm.app.shared.generated.resources.tools_b_port_5900
import networkhelm.app.shared.generated.resources.tools_b_port_5938
import networkhelm.app.shared.generated.resources.tools_b_port_62078
import networkhelm.app.shared.generated.resources.tools_b_port_631
import networkhelm.app.shared.generated.resources.tools_b_port_636
import networkhelm.app.shared.generated.resources.tools_b_port_6379
import networkhelm.app.shared.generated.resources.tools_b_port_6443
import networkhelm.app.shared.generated.resources.tools_b_port_67
import networkhelm.app.shared.generated.resources.tools_b_port_68
import networkhelm.app.shared.generated.resources.tools_b_port_6881
import networkhelm.app.shared.generated.resources.tools_b_port_69
import networkhelm.app.shared.generated.resources.tools_b_port_7547
import networkhelm.app.shared.generated.resources.tools_b_port_80
import networkhelm.app.shared.generated.resources.tools_b_port_8000
import networkhelm.app.shared.generated.resources.tools_b_port_8006
import networkhelm.app.shared.generated.resources.tools_b_port_8008
import networkhelm.app.shared.generated.resources.tools_b_port_8080
import networkhelm.app.shared.generated.resources.tools_b_port_8081
import networkhelm.app.shared.generated.resources.tools_b_port_8083
import networkhelm.app.shared.generated.resources.tools_b_port_8088
import networkhelm.app.shared.generated.resources.tools_b_port_81
import networkhelm.app.shared.generated.resources.tools_b_port_8123
import networkhelm.app.shared.generated.resources.tools_b_port_8291
import networkhelm.app.shared.generated.resources.tools_b_port_8443
import networkhelm.app.shared.generated.resources.tools_b_port_853
import networkhelm.app.shared.generated.resources.tools_b_port_8728
import networkhelm.app.shared.generated.resources.tools_b_port_8729
import networkhelm.app.shared.generated.resources.tools_b_port_873
import networkhelm.app.shared.generated.resources.tools_b_port_88
import networkhelm.app.shared.generated.resources.tools_b_port_8883
import networkhelm.app.shared.generated.resources.tools_b_port_9000
import networkhelm.app.shared.generated.resources.tools_b_port_902
import networkhelm.app.shared.generated.resources.tools_b_port_9090
import networkhelm.app.shared.generated.resources.tools_b_port_9091
import networkhelm.app.shared.generated.resources.tools_b_port_9100
import networkhelm.app.shared.generated.resources.tools_b_port_9200
import networkhelm.app.shared.generated.resources.tools_b_port_9418
import networkhelm.app.shared.generated.resources.tools_b_port_993
import networkhelm.app.shared.generated.resources.tools_b_port_995

internal val PortDescriptions: Map<Int, StringResource> = mapOf(
    20 to Res.string.tools_b_port_20,
    21 to Res.string.tools_b_port_21,
    22 to Res.string.tools_b_port_22,
    23 to Res.string.tools_b_port_23,
    25 to Res.string.tools_b_port_25,
    53 to Res.string.tools_b_port_53,
    67 to Res.string.tools_b_port_67,
    68 to Res.string.tools_b_port_68,
    69 to Res.string.tools_b_port_69,
    80 to Res.string.tools_b_port_80,
    81 to Res.string.tools_b_port_81,
    88 to Res.string.tools_b_port_88,
    110 to Res.string.tools_b_port_110,
    111 to Res.string.tools_b_port_111,
    119 to Res.string.tools_b_port_119,
    123 to Res.string.tools_b_port_123,
    135 to Res.string.tools_b_port_135,
    137 to Res.string.tools_b_port_137,
    138 to Res.string.tools_b_port_138,
    139 to Res.string.tools_b_port_139,
    143 to Res.string.tools_b_port_143,
    161 to Res.string.tools_b_port_161,
    162 to Res.string.tools_b_port_162,
    179 to Res.string.tools_b_port_179,
    194 to Res.string.tools_b_port_194,
    389 to Res.string.tools_b_port_389,
    443 to Res.string.tools_b_port_443,
    445 to Res.string.tools_b_port_445,
    465 to Res.string.tools_b_port_465,
    500 to Res.string.tools_b_port_500,
    514 to Res.string.tools_b_port_514,
    515 to Res.string.tools_b_port_515,
    520 to Res.string.tools_b_port_520,
    546 to Res.string.tools_b_port_546,
    547 to Res.string.tools_b_port_547,
    548 to Res.string.tools_b_port_548,
    554 to Res.string.tools_b_port_554,
    587 to Res.string.tools_b_port_587,
    631 to Res.string.tools_b_port_631,
    636 to Res.string.tools_b_port_636,
    853 to Res.string.tools_b_port_853,
    873 to Res.string.tools_b_port_873,
    902 to Res.string.tools_b_port_902,
    993 to Res.string.tools_b_port_993,
    995 to Res.string.tools_b_port_995,
    1080 to Res.string.tools_b_port_1080,
    1194 to Res.string.tools_b_port_1194,
    1433 to Res.string.tools_b_port_1433,
    1521 to Res.string.tools_b_port_1521,
    1701 to Res.string.tools_b_port_1701,
    1723 to Res.string.tools_b_port_1723,
    1812 to Res.string.tools_b_port_1812,
    1813 to Res.string.tools_b_port_1813,
    1883 to Res.string.tools_b_port_1883,
    1900 to Res.string.tools_b_port_1900,
    2049 to Res.string.tools_b_port_2049,
    2181 to Res.string.tools_b_port_2181,
    2375 to Res.string.tools_b_port_2375,
    2376 to Res.string.tools_b_port_2376,
    3000 to Res.string.tools_b_port_3000,
    3128 to Res.string.tools_b_port_3128,
    3306 to Res.string.tools_b_port_3306,
    3389 to Res.string.tools_b_port_3389,
    3478 to Res.string.tools_b_port_3478,
    4443 to Res.string.tools_b_port_4443,
    4500 to Res.string.tools_b_port_4500,
    5000 to Res.string.tools_b_port_5000,
    5001 to Res.string.tools_b_port_5001,
    5060 to Res.string.tools_b_port_5060,
    5061 to Res.string.tools_b_port_5061,
    5201 to Res.string.tools_b_port_5201,
    5222 to Res.string.tools_b_port_5222,
    5353 to Res.string.tools_b_port_5353,
    5432 to Res.string.tools_b_port_5432,
    5683 to Res.string.tools_b_port_5683,
    5900 to Res.string.tools_b_port_5900,
    5938 to Res.string.tools_b_port_5938,
    6379 to Res.string.tools_b_port_6379,
    6443 to Res.string.tools_b_port_6443,
    6881 to Res.string.tools_b_port_6881,
    7547 to Res.string.tools_b_port_7547,
    8000 to Res.string.tools_b_port_8000,
    8006 to Res.string.tools_b_port_8006,
    8008 to Res.string.tools_b_port_8008,
    8080 to Res.string.tools_b_port_8080,
    8081 to Res.string.tools_b_port_8081,
    8083 to Res.string.tools_b_port_8083,
    8088 to Res.string.tools_b_port_8088,
    8123 to Res.string.tools_b_port_8123,
    8291 to Res.string.tools_b_port_8291,
    8443 to Res.string.tools_b_port_8443,
    8728 to Res.string.tools_b_port_8728,
    8729 to Res.string.tools_b_port_8729,
    8883 to Res.string.tools_b_port_8883,
    9000 to Res.string.tools_b_port_9000,
    9090 to Res.string.tools_b_port_9090,
    9091 to Res.string.tools_b_port_9091,
    9100 to Res.string.tools_b_port_9100,
    9200 to Res.string.tools_b_port_9200,
    9418 to Res.string.tools_b_port_9418,
    10000 to Res.string.tools_b_port_10000,
    10050 to Res.string.tools_b_port_10050,
    10051 to Res.string.tools_b_port_10051,
    11211 to Res.string.tools_b_port_11211,
    25565 to Res.string.tools_b_port_25565,
    27017 to Res.string.tools_b_port_27017,
    32400 to Res.string.tools_b_port_32400,
    51820 to Res.string.tools_b_port_51820,
    62078 to Res.string.tools_b_port_62078,
)
