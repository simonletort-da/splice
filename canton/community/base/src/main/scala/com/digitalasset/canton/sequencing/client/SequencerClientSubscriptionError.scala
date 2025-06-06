// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.sequencing.client

import com.digitalasset.canton.data.CantonTimestamp
import com.digitalasset.canton.logging.pretty.{Pretty, PrettyPrinting}
import com.digitalasset.canton.sequencing.SequencerAggregator.SequencerAggregatorError

sealed trait SequencerClientSubscriptionError extends Product with Serializable {
  def mbException: Option[Throwable] = None
}

object SequencerClientSubscriptionError {
  final case class EventAggregationError(error: SequencerAggregatorError)
      extends SequencerClientSubscriptionError

  final case class EventValidationError(error: SequencedEventValidationError[Nothing])
      extends SequencerClientSubscriptionError

  sealed trait ApplicationHandlerFailure
      extends SequencerClientSubscriptionError
      with PrettyPrinting

  /** The application handler returned that it is being shutdown. */
  case object ApplicationHandlerShutdown extends ApplicationHandlerFailure {
    override protected def pretty: Pretty[ApplicationHandlerShutdown.type] =
      prettyOfObject[ApplicationHandlerShutdown.type]
  }

  sealed trait ApplicationHandlerError extends ApplicationHandlerFailure

  /** The application handler returned that it is being passive. */
  final case class ApplicationHandlerPassive(reason: String) extends ApplicationHandlerError {
    override protected def pretty: Pretty[ApplicationHandlerPassive] =
      prettyOfClass(param("reason", _.reason.unquoted))
  }

  /** The application handler threw an exception while processing the event (synchronously or
    * asynchronously)
    */
  final case class ApplicationHandlerException(
      exception: Throwable,
      firstSequencingTimestamp: CantonTimestamp,
      lastSequencingTimestamp: CantonTimestamp,
  ) extends ApplicationHandlerError {
    override def mbException: Option[Throwable] = Some(exception)

    override protected def pretty: Pretty[ApplicationHandlerException] = prettyOfClass(
      param("first sequencing timestamp", _.firstSequencingTimestamp),
      param("last sequencing timestamp", _.lastSequencingTimestamp),
      unnamedParam(_.exception),
    )
  }

}
