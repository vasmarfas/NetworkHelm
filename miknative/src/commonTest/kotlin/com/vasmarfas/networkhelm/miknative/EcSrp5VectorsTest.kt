package com.vasmarfas.networkhelm.miknative

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// vectors of an independent implementation of the login on arbitrary-precision integers
class EcSrp5VectorsTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun login(
        ephemeral: String,
        hello: String,
        challenge: String,
        confirmation: String,
        answer: String,
        user: String = "admin",
        password: String = "secret",
    ): StreamKeys {
        val client = EcSrp5Client(user, password, U256.fromBytes(hex(ephemeral)))
        assertContentEquals(hex(hello), client.hello())
        assertContentEquals(hex(confirmation), client.confirm(hex(challenge)))
        assertTrue(client.verify(hex(answer)))
        return client.keys()
    }

    private fun assertKeys(keys: StreamKeys, send: Pair<String, String>, receive: Pair<String, String>) {
        assertContentEquals(hex(send.first), keys.send.aes)
        assertContentEquals(hex(send.second), keys.send.mac)
        assertContentEquals(hex(receive.first), keys.receive.aes)
        assertContentEquals(hex(receive.second), keys.receive.mac)
    }

    @Test
    fun aLoginGivesTheConfirmationsAndKeysOfTheVectors() {
        val keys = login(
            ephemeral = "5704de18fc6045e4d08c3a261162795689ba6a0383964109350f8fad3c4ad972",
            hello = "61646d696e0021eb0ccf3c4231fb8d09012b51c920df4fb44d2c9a1eb2965ee36c6d4211712001",
            challenge = "369087411810cf36ca6439f706778d6743fa7328a24844d503b58126686199a500000102030405060708090a0b0c0d0e0f",
            confirmation = "6046c34bcabe3e6930aca89c3746da24b2f2bab298b0ce9e83cb580475ca6b65",
            answer = "296507ac7efcb30f737b08315d35e016b26634e0159ab68a572c2a454783e506",
        )
        assertKeys(
            keys,
            send = "d7f0a3c244387b5d72e1fcc9f33345e2" to "614f55a1ea0abae04dc903648bdbb6a8ff14b8ba",
            receive = "a2faf6f3aea1b56ad359f23a6664bd58" to "f7a8a17416983221cba490ddb86ee0fdac0e9b74",
        )
    }

    @Test
    fun aSharedValueWithAZeroFirstByteIsHashedWithoutIt() {
        val keys = login(
            ephemeral = "59013efb3130e9a04b137b6718ba385bfcbff9cad475a0fc15197d818bbf560e",
            hello = "61646d696e0071bea892da4c18fe917314d87df0950b11eb4988d276a8c168347924d7f7a3d500",
            challenge = "4604a7e3e3a842f3fbb17e5d483380ff3386750cff7e19f77ed24a439ca2447701000102030405060708090a0b0c0d0e0f",
            confirmation = "66eac0ecf29e4b60edb144da4952301d2024d2145472246ce9562dbf1fb22699",
            answer = "68fdfe7913d91a11bae76f911b9f6aad2181eba30f8f467179adef6220889c42",
        )
        assertKeys(
            keys,
            send = "44ed34aaf1bfc5c5e14194f644c73965" to "f8e56c2d2b43e58699ca405c0726335f956633ac",
            receive = "358dd78c90d5c6d9201610c7aca087f1" to "25cec8a9721fb5135e0b06bd052a9a2d73a8ad55",
        )
    }

    @Test
    fun aRecordOfTheVectorsIsOpenedAndItsMessageIsTheEncodedRequest() {
        val message = M2Message.of(
            M2Key.To to M2Value.U32s(longArrayOf(20, 0)),
            M2Key.Command to M2Value.U32(0xFE0004),
            M2Key.Request to M2Value.Bool(true),
            M2Key.RequestId to M2Value.U8(1),
            M2Key.From to M2Value.U32s(longArrayOf(0, 16)),
            0xFE000C to M2Value.U32(0x10000005),
        ).encode()
        assertContentEquals(hex("4d320100ff88020014000000000000000700ff080400fe000500ff010600ff09010200ff88020000000000100000000c00fe0805000010"), message)
        val sealer = DirectionKeys(hex("d7f0a3c244387b5d72e1fcc9f33345e2"), hex("614f55a1ea0abae04dc903648bdbb6a8ff14b8ba"))
        val channel = SecureChannel(StreamKeys(send = DirectionKeys(ByteArray(16), ByteArray(20)), receive = sealer))
        val opened = channel.open(hex("0050101112131415161718191a1b1c1d1e1fe7c5fb39c31d06656458eec96200c4f93e66da8b23f58f3f6bb2e436b5f29eb5ec18aee32918da3abc6731d48bfa58b8f0dc3f162f22cc6c8bb27b5fb0c1881f191cba8db8cd5979aea99a86ef72712d"))
        assertEquals(1, opened.size)
        assertContentEquals(message, opened.single())
    }

    @Test
    fun aNameWithASuffixAndAPasswordOutsideAsciiIsProvedForTheBareAccount() {
        val keys = login(
            ephemeral = "8864d93a3141e5f6ed2749b5c0a82ad383838ab7f3cc7fff17167f7d578f3ba7",
            hello = "616c6963652b6374007e1f6510d5ddf22b9fe3a5d545285950983ce5e00dda189a05fcf66c5b4978da00",
            challenge = "5f640504a2f1daafcc862235efb7b1da7aa63de05bdc338f80be45184fd258de000f1e2d3c4b5a69788796a5b4c3d2e1f0",
            confirmation = "20a3caf79c31017c04a840b1b2a2220f0fb6ae0fac685ea113614688bae32eb8",
            answer = "1b2c9403a33d84bfdde5f559d2938d82627c85d6865e5b68d8bbc3c517ddd9b3",
            user = "alice+ct",
            password = "Пароль-1",
        )
        assertKeys(
            keys,
            send = "29ea9a0829f8b45b43152bf241fb2302" to "2965d2aebf51aa2458f392fc478d928d1e4a7ad2",
            receive = "9354fd9864d726ba115d7ca8f657c1f2" to "85291f78287c8f8d34602e6db39d7dc3578f628d",
        )
    }

    @Test
    fun aSuffixLoginWithAZeroFirstByteOfTheSharedValueFollowsTheSameRule() {
        val keys = login(
            ephemeral = "644691dbf8049f4881ff7c49193a504be02ca6fc7692db271c96086abd6a9b09",
            hello = "616c6963652b6374001bbf6a6378220835030e0218674b66b41cb65f44ca3884f86ac30bf43d240f8601",
            challenge = "57418ae45f34ab2dcda85e12703bdb83f3800698d07c00e5d72d8b8bf136168d010f1e2d3c4b5a69788796a5b4c3d2e1f0",
            confirmation = "eb967dc457a3ed6d034e4228392410c2fb2b2160692267f6baba31e50dc96608",
            answer = "7a7950dfd5a075ba1d33d4455ba24ac9df5b8e08946496e6347c09a05eab4a55",
            user = "alice+ct",
            password = "Пароль-1",
        )
        assertKeys(
            keys,
            send = "13850b21ad29e93b4872ed43262a9dc6" to "b1772ae448c73376283600e2e037862b29c31271",
            receive = "7acbb5e22dbbeef511f066c3a93a5d6a" to "a2075149c97b0588c1dfefcaebcb3a8b0fe8f30e",
        )
    }
}
