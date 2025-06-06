// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.topology.processing

import com.digitalasset.canton.SequencerCounter
import com.digitalasset.canton.lifecycle.FutureUnlessShutdown
import com.digitalasset.canton.topology.transaction.SignedTopologyTransaction.GenericSignedTopologyTransaction
import com.digitalasset.canton.tracing.TraceContext

trait TopologyTransactionProcessingSubscriber {

  /** Move the highest known (effective / approximate) timestamp ahead in the future.
    *
    * May only be called if:
    *   1. All committed topology transactions with effective time up to `effectiveTimestamp` have
    *      been persisted in the topology store.
    *   1. All committed topology transactions with sequenced time up to `sequencedTimestamp` have
    *      been persisted in the topology store.
    *   1. If this method is called with `potentialTopologyChange == true`, then for every
    *      subsequent committed topology transaction either `updateHead(potentialTopologyChange ==
    *      true, ...)` or `observed` must be called again; such calls must occur with ascending
    *      effective timestamps.
    *   1. `sequencedTimestamp <= effectiveTimestamp`
    *   1. `approximateTimestamp <= effectiveTimestamp`
    *   1. A sequenced event with timestamp at least `approximateTimestamp` has been received from
    *      the sequencer.
    */
  def updateHead(
      sequencedTimestamp: SequencedTime,
      effectiveTimestamp: EffectiveTime,
      approximateTimestamp: ApproximateTime,
      potentialTopologyChange: Boolean,
  )(implicit traceContext: TraceContext): Unit = ()

  /** This must be called whenever a topology transaction is committed. It may be called at
    * additional timestamps with `transactions` being empty. Calls must have strictly increasing
    * `sequencedTimestamp` and `effectiveTimestamp`. The `effectiveTimestamp` must be the one
    * computed by
    * [[com.digitalasset.canton.topology.processing.TopologyTimestampPlusEpsilonTracker]] for
    * `sequencedTimestamp`.
    *
    * During crash recovery previous calls of this method may be replayed. Therefore,
    * implementations must be idempotent.
    */
  def observed(
      sequencedTimestamp: SequencedTime,
      effectiveTimestamp: EffectiveTime,
      sequencerCounter: SequencerCounter,
      transactions: Seq[GenericSignedTopologyTransaction],
  )(implicit traceContext: TraceContext): FutureUnlessShutdown[Unit]

  /** The order in which the subscriber should be executed among all the subscriptions. Lower values
    * are executed first.
    */
  def executionOrder: Int = 10
}
