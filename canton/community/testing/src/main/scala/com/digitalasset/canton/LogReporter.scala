// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton

import com.digitalasset.canton.logging.NamedLoggerFactory
import com.typesafe.scalalogging.Logger
import org.scalatest.Reporter
import org.scalatest.events.*

/** Logs when a test case or suite is started or completed. To use it, register this class with "-C"
  * when ScalaTest is started.
  */
class LogReporter extends Reporter {

  // We do not use `NamedLoggerFactory.root` since that ends up with log messages
  // that lnav interprets as java_log instead of canton_log.
  // Specifically, setting the name below produces a log line of the form
  // INFO  c.d.c.LogReporter:reporter=scala-test - Starting test run...
  // and the colon after the class name does not fit lnav's predefined
  // `java_log` format.
  private[this] val logger: Logger =
    NamedLoggerFactory("reporter", "scala-test").getLogger(getClass)

  override def apply(event: Event): Unit = event match {
    case _: RunStarting => logger.info("Starting test run...")
    case _: RunCompleted => logger.info("Completed test run.")
    case _: RunStopped => logger.warn("Stopped test run.")
    case event: RunAborted => warnWithThrowable("Aborted test run.", event.throwable)
    case event: SuiteStarting => logger.info(s"Starting test suite '${event.suiteName}'...")
    case event: SuiteCompleted => logger.info(s"Completed test suite '${event.suiteName}'.")
    case event: SuiteAborted =>
      warnWithThrowable(s"Aborted test suite '${event.suiteName}'.", event.throwable)
    case event: ScopeOpened => logger.info(s"Entering '${event.message}'")
    case event: ScopeClosed => logger.info(s"Leaving '${event.message}'")
    case event: TestStarting => logger.info(s"Starting '${event.suiteName}/${event.testName}'...")
    case event: TestSucceeded =>
      logger.info(s"Test succeeded: '${event.suiteName}/${event.testName}'")
    case event: TestFailed =>
      val locationMsg = event.location.map(l => s", location: $l").getOrElse("")
      val msg =
        s"Test failed: '${event.suiteName}/${event.testName}', message: ${event.message}$locationMsg"
      event.throwable
        .map(cause => logger.warn(msg, cause))
        .getOrElse(logger.warn(msg))
    case event: TestCanceled =>
      logger.info(s"Test canceled: '${event.suiteName}/${event.testName}'")
    case event: TestIgnored => logger.info(s"Test ignored: '${event.suiteName}/${event.testName}'")
    case _ =>
  }

  private def warnWithThrowable(message: String, throwableO: Option[Throwable]): Unit =
    throwableO match {
      case Some(throwable) => logger.warn(message, throwable)
      case None => logger.warn(message)
    }
}
