// Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package org.lfdecentralizedtrust.splice.scan.automation

import org.apache.pekko.stream.Materializer
import org.lfdecentralizedtrust.splice.automation.{
  AutomationServiceCompanion,
  SpliceAppAutomationService,
  SqlIndexInitializationTrigger,
  TxLogBackfillingTrigger,
}
import org.lfdecentralizedtrust.splice.config.UpgradesConfig
import org.lfdecentralizedtrust.splice.environment.{RetryProvider, SpliceLedgerClient}
import org.lfdecentralizedtrust.splice.http.HttpClient
import org.lfdecentralizedtrust.splice.scan.config.ScanAppBackendConfig
import org.lfdecentralizedtrust.splice.store.{
  DomainTimeSynchronization,
  DomainUnpausedSynchronization,
}
import org.lfdecentralizedtrust.splice.scan.store.{AcsSnapshotStore, ScanStore}
import org.lfdecentralizedtrust.splice.util.TemplateJsonDecoder
import com.digitalasset.canton.logging.NamedLoggerFactory
import com.digitalasset.canton.resource.Storage
import com.digitalasset.canton.time.Clock
import com.digitalasset.canton.topology.PartyId
import io.opentelemetry.api.trace.Tracer

import scala.concurrent.ExecutionContextExecutor

/** Manages background automation that runs on a CC Scan app. */
class ScanAutomationService(
    config: ScanAppBackendConfig,
    clock: Clock,
    ledgerClient: SpliceLedgerClient,
    retryProvider: RetryProvider,
    protected val loggerFactory: NamedLoggerFactory,
    store: ScanStore,
    storage: Storage,
    snapshotStore: AcsSnapshotStore,
    ingestFromParticipantBegin: Boolean,
    ingestUpdateHistoryFromParticipantBegin: Boolean,
    svParty: PartyId,
    svName: String,
    upgradesConfig: UpgradesConfig,
)(implicit
    ec: ExecutionContextExecutor,
    mat: Materializer,
    tracer: Tracer,
    httpClient: HttpClient,
    templateJsonDecoder: TemplateJsonDecoder,
) extends SpliceAppAutomationService(
      config.automation,
      clock,
      // scan only does reads so no need to block anything.
      DomainTimeSynchronization.Noop,
      DomainUnpausedSynchronization.Noop,
      store,
      ledgerClient,
      retryProvider,
      ingestFromParticipantBegin,
      ingestUpdateHistoryFromParticipantBegin,
    ) {
  override def companion
      : org.lfdecentralizedtrust.splice.scan.automation.ScanAutomationService.type =
    ScanAutomationService

  registerTrigger(new ScanAggregationTrigger(store, triggerContext))
  registerTrigger(new ScanBackfillAggregatesTrigger(store, triggerContext))
  if (config.updateHistoryBackfillEnabled) {
    registerTrigger(
      new ScanHistoryBackfillingTrigger(
        store,
        svName,
        ledgerClient,
        config.updateHistoryBackfillBatchSize,
        config.updateHistoryBackfillImportUpdatesEnabled,
        svParty,
        upgradesConfig,
        triggerContext,
      )
    )
  }
  registerTrigger(
    new AcsSnapshotTrigger(
      snapshotStore,
      store.updateHistory,
      config.acsSnapshotPeriodHours,
      config.updateHistoryBackfillEnabled,
      triggerContext,
    )
  )
  if (config.txLogBackfillEnabled) {
    registerTrigger(
      new TxLogBackfillingTrigger(
        store,
        config.txLogBackfillBatchSize,
        triggerContext,
      )
    )
  }
  registerTrigger(
    SqlIndexInitializationTrigger(
      storage,
      triggerContext,
    )
  )
}

object ScanAutomationService extends AutomationServiceCompanion {
  override protected[this] def expectedTriggerClasses: Seq[Nothing] = Seq.empty
}
