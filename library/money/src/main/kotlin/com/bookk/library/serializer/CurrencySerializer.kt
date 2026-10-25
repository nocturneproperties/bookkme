package com.bookk.library.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.joda.money.CurrencyUnit
import org.joda.money.Money

object CurrencySerializer : KSerializer<CurrencyUnit> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CurrencyUnit", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): CurrencyUnit {
        return CurrencyUnit.of(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: CurrencyUnit) {
        encoder.encodeString(value.code)
    }
}

object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Money {
        return Money.parse(decoder.decodeString())
    }

    override fun serialize(encoder: Encoder, value: Money) {
        encoder.encodeString(value.toString())
    }
}