// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.protocol

import com.digitalasset.canton.ProtoDeserializationError
import com.digitalasset.canton.data.{DeduplicationPeriod, Offset}
import com.digitalasset.canton.serialization.ProtoConverter.{DurationConverter, ParsingResult}

final case class SerializableDeduplicationPeriod(deduplicationPeriod: DeduplicationPeriod) {
  def toProtoV30: v30.DeduplicationPeriod = deduplicationPeriod match {
    case duration: DeduplicationPeriod.DeduplicationDuration =>
      v30.DeduplicationPeriod(
        v30.DeduplicationPeriod.Period.Duration(
          DurationConverter.toProtoPrimitive(duration.duration)
        )
      )
    case offset: DeduplicationPeriod.DeduplicationOffset =>
      v30.DeduplicationPeriod(
        v30.DeduplicationPeriod.Period.Offset(
          offset.offset.fold(0L)(_.unwrap)
        )
      )
  }
}
object SerializableDeduplicationPeriod {
  def fromProtoV30(
      deduplicationPeriodP: v30.DeduplicationPeriod
  ): ParsingResult[DeduplicationPeriod] = {
    val dedupP = v30.DeduplicationPeriod.Period
    deduplicationPeriodP.period match {
      case dedupP.Empty => Left(ProtoDeserializationError.FieldNotSet("DeduplicationPeriod.value"))
      case dedupP.Duration(duration) =>
        DurationConverter
          .fromProtoPrimitive(duration)
          .map(DeduplicationPeriod.DeduplicationDuration.apply)
      case dedupP.Offset(offset) =>
        if (offset == 0)
          Right(DeduplicationPeriod.DeduplicationOffset(None))
        else
          Offset
            .fromLong(offset)
            .map(Some(_))
            .map(DeduplicationPeriod.DeduplicationOffset.apply)
            .left
            .map(ProtoDeserializationError.ValueConversionError("deduplication_period", _))
    }
  }
}
