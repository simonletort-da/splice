// Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package org.lfdecentralizedtrust.splice.environment.ledger.api

import org.lfdecentralizedtrust.splice.util.PrettyInstances.*
import com.digitalasset.canton.logging.pretty.{Pretty, PrettyInstances, PrettyPrinting}
import com.daml.ledger.api.v2.reassignment as multidomain
import com.digitalasset.canton.data.CantonTimestamp
import io.grpc.Status

final case class Reassignment[+E](
    updateId: String,
    offset: Long,
    recordTime: CantonTimestamp,
    event: E & ReassignmentEvent,
) extends PrettyPrinting {
  override def pretty: Pretty[this.type] =
    prettyOfClass(
      param("updateId", (x: this.type) => x.updateId)(PrettyInstances.prettyString),
      param("offset", _.offset),
      param("event", _.event),
    )
}

object Reassignment {
  private[splice] def fromProto(
      proto: multidomain.Reassignment
  ): Reassignment[ReassignmentEvent] = {
    // TODO(DACH-NY/canton-network-internal#361) Support reassignment batching
    val singleEvent = proto.events match {
      case Seq(e) => e
      case events =>
        throw Status.INTERNAL
          .withDescription(s"Reassignment batching is not currently supported: $events")
          .asRuntimeException
    }
    val event = singleEvent.event match {
      case multidomain.ReassignmentEvent.Event.Unassigned(out) =>
        ReassignmentEvent.Unassign.fromProto(out)
      case multidomain.ReassignmentEvent.Event.Assigned(in) =>
        ReassignmentEvent.Assign.fromProto(in)
      case multidomain.ReassignmentEvent.Event.Empty =>
        throw new IllegalArgumentException("uninitialized transfer event")
    }
    val recordTime = CantonTimestamp
      .fromProtoTimestamp(
        proto.recordTime
          .getOrElse(
            throw new IllegalArgumentException(
              s"transfer event ${proto.updateId} without record time"
            )
          )
      )
      .getOrElse(
        throw new IllegalArgumentException(
          s"transfer event ${proto.updateId} with invalid record time"
        )
      )
    Reassignment(
      proto.updateId,
      proto.offset,
      recordTime,
      event,
    )
  }
}
