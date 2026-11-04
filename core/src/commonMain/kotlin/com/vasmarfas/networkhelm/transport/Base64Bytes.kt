package com.vasmarfas.networkhelm.transport

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

object Base64Bytes : KSerializer<ByteArray> {
    override val descriptor = PrimitiveSerialDescriptor("com.vasmarfas.networkhelm.transport.Base64Bytes", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ByteArray) = encoder.encodeString(Base64.encode(value))

    override fun deserialize(decoder: Decoder): ByteArray = Base64.decode(decoder.decodeString())
}
