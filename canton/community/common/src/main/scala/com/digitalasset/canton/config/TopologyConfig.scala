// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.config

import com.digitalasset.canton.config.RequireTypes.PositiveInt
import com.digitalasset.canton.config.TopologyConfig.*
import com.digitalasset.canton.config.manual.CantonConfigValidatorDerivation

import scala.concurrent.duration.Duration

/** @param topologyTransactionRegistrationTimeout
  *   Used to determine the max sequencing time for topology transaction broadcasts.
  * @param broadcastBatchSize
  *   The maximum number of topology transactions sent in a topology transaction broadcast
  */
final case class TopologyConfig(
    topologyTransactionRegistrationTimeout: NonNegativeDuration =
      defaultTopologyTransactionRegistrationTimeout,
    broadcastBatchSize: PositiveInt = defaultBroadcastBatchSize,
) extends UniformCantonConfigValidation

object TopologyConfig {
  implicit val topologyConfigCantonConfigValidator: CantonConfigValidator[TopologyConfig] = {
    import CantonConfigValidatorInstances.*
    CantonConfigValidatorDerivation[TopologyConfig]
  }

  private[TopologyConfig] val defaultTopologyTransactionRegistrationTimeout =
    NonNegativeDuration.ofSeconds(20)

  val defaultBroadcastBatchSize = PositiveInt.tryCreate(100)

  def NotUsed: TopologyConfig = TopologyConfig(topologyTransactionRegistrationTimeout =
    NonNegativeDuration.tryFromDuration(Duration.Inf)
  )
}
