// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.logging

import com.digitalasset.base.error.{BaseError, BaseErrorLogger}
import com.digitalasset.canton.tracing.TraceContext
import com.typesafe.scalalogging.Logger
import org.slf4j
import org.slf4j.MDC
import org.slf4j.event.Level
import org.slf4j.helpers.NOPLogger

/** Enriches a [[com.digitalasset.canton.tracing.TraceContext]] with a fixed logger and a set of
  * properties. Use this class as an implicit parameter of methods inside helper classes whose class
  * name shall not show up in the log line as part of the logger name. Instead, the logger name and
  * properties are fixed when this object is created, which typically happens at a call site further
  * up via [[NamedLogging.errorLoggingContext]].
  * @see
  *   [[NamedLoggingContext]] for another variant where the logger name is not fixed
  * @see
  *   [[NamedLogging.errorLoggingContext]] converts
  */
trait ErrorLoggingContext extends BaseErrorLogger {
  def properties: Map[String, String]
  def correlationId: Option[String]
  def traceId: Option[String]
  def traceContext: TraceContext

  def debug(message: String): Unit
  def debug(message: String, throwable: Throwable): Unit
  def info(message: String): Unit
  def info(message: String, throwable: Throwable): Unit
  def warn(message: String): Unit
  def warn(message: String, throwable: Throwable): Unit
  def error(message: String): Unit
  def error(message: String, throwable: Throwable): Unit
  def withContext[A](context: Map[String, String])(body: => A): A
  def noTracingLogger: Logger
  def logger: TracedLogger
}

abstract class AbstractErrorLoggingContext(
    logger: TracedLogger,
    properties: Map[String, String],
    traceContext: TraceContext,
) extends ErrorLoggingContext {

  override def traceId: Option[String] = traceContext.traceId

  /** Log the cause while adding the context into the MDC
    *
    * We add the context twice to the MDC: first, every map item is added directly and then we add a
    * second string version as "err-context". When we log to file, we add the err-context to the log
    * output. When we log to JSON, we ignore the err-context field.
    */
  override def logError(err: BaseError, extra: Map[String, String]): Unit = {
    implicit val traceContextImplicit: TraceContext = traceContext

    val mergedContext = err.context ++ err.location.map(("location", _)).toList.toMap ++ extra
    // we are putting the context into the MDC twice, once as a serialised string, once argument by argument
    // for text logging, we'll use the err-context string, for json logging, we use the arguments and ignore the err-context
    val arguments = mergedContext ++ Map(
      "error-code" -> err.code.codeStr(correlationId),
      "err-context" -> ("{" + ErrorLoggingContext.formatContextAsString(mergedContext) + "}"),
    ) ++ properties
    val message = err.code.toMsg(err.cause, correlationId, None)
    withContext(arguments) {
      (err.code.logLevel, err.throwableO) match {
        case (Level.INFO, None) => logger.info(message)
        case (Level.INFO, Some(tr)) => logger.info(message, tr)
        case (Level.WARN, None) => logger.warn(message)
        case (Level.WARN, Some(tr)) => logger.warn(message, tr)
        // an error that is logged with < INFO is not an error ...
        case (_, None) => logger.error(message)
        case (_, Some(tr)) => logger.error(message, tr)
      }
    }
  }

  override def info(message: String): Unit = logger.info(message)(traceContext)
  override def info(message: String, throwable: Throwable): Unit =
    logger.info(message, throwable)(traceContext)
  override def warn(message: String): Unit = logger.warn(message)(traceContext)
  override def warn(message: String, throwable: Throwable): Unit =
    logger.warn(message, throwable)(traceContext)
  override def error(message: String): Unit = logger.error(message)(traceContext)
  override def error(message: String, throwable: Throwable): Unit =
    logger.error(message, throwable)(traceContext)

  def debug(message: String): Unit = logger.debug(message)(traceContext)
  def debug(message: String, throwable: Throwable): Unit =
    logger.debug(message, throwable)(traceContext)
  def trace(message: String): Unit = logger.trace(message)(traceContext)
  def trace(message: String, throwable: Throwable): Unit =
    logger.trace(message, throwable)(traceContext)

  def withContext[A](context: Map[String, String])(body: => A): A = {
    context.foreach { case (name, value) =>
      MDC.put(name, value)
    }
    try body
    finally context.keys.foreach(key => MDC.remove(key))
  }

  override def correlationId: Option[String] = traceContext.traceId

  def noTracingLogger: Logger = NamedLogging.loggerWithoutTracing(logger)

}

object ErrorLoggingContext {

  private final case class Impl(
      logger: TracedLogger,
      properties: Map[String, String],
      traceContext: TraceContext,
  ) extends AbstractErrorLoggingContext(logger, properties, traceContext)

  private final case class WithExplicitCorrelationId(
      logger: TracedLogger,
      properties: Map[String, String],
      traceContext: TraceContext,
      explicitCorrelationId: String,
  ) extends AbstractErrorLoggingContext(logger, properties, traceContext) {
    override def correlationId: Option[String] = Some(explicitCorrelationId)
  }

  def apply(
      logger: TracedLogger,
      loggingContext: LoggingContextWithTrace,
  ): ErrorLoggingContext =
    Impl(logger, loggingContext.toPropertiesMap, loggingContext.traceContext)

  def apply(
      logger: TracedLogger,
      properties: Map[String, String],
      traceContext: TraceContext,
  ): ErrorLoggingContext = Impl(logger, properties, traceContext)

  def withExplicitCorrelationId(
      logger: TracedLogger,
      properties: Map[String, String],
      traceContext: TraceContext,
      explicitCorrelationId: String,
  ): ErrorLoggingContext =
    WithExplicitCorrelationId(logger, properties, traceContext, explicitCorrelationId)

  def forClass(
      loggerFactory: NamedLoggerFactory,
      clazz: Class[_],
      properties: Map[String, String] = Map.empty,
      traceContext: TraceContext = TraceContext.empty,
  ): ErrorLoggingContext =
    Impl(
      TracedLogger(loggerFactory.getLogger(clazz)),
      properties,
      traceContext,
    )

  def fromTracedLogger(tracedLogger: TracedLogger)(implicit
      traceContext: TraceContext
  ): ErrorLoggingContext =
    ErrorLoggingContext(tracedLogger, Map.empty, traceContext)

  def fromOption(
      logger: TracedLogger,
      loggingContextWithTrace: LoggingContextWithTrace,
      submissionIdO: Option[String],
  ): ErrorLoggingContext = submissionIdO match {
    case Some(submissionId) =>
      WithExplicitCorrelationId(
        logger,
        loggingContextWithTrace.toPropertiesMap,
        loggingContextWithTrace.traceContext,
        submissionId,
      )
    case None =>
      ErrorLoggingContext(
        logger,
        loggingContextWithTrace.toPropertiesMap,
        loggingContextWithTrace.traceContext,
      )
  }

  /** Formats the context as a string for logging */
  def formatContextAsString(contextMap: Map[String, String]): String =
    contextMap
      .filter(_._2.nonEmpty)
      .toSeq
      .sortBy(_._1)
      .map { case (k, v) =>
        s"$k=$v"
      }
      .mkString(", ")

}

class NoLogging(
    val properties: Map[String, String],
    val correlationId: Option[String],
    val traceId: Option[String] = None,
) extends ErrorLoggingContext {
  private val underlying: slf4j.Logger = NOPLogger.NOP_LOGGER

  override def logError(err: BaseError, extra: Map[String, String]): Unit = ()
  override def debug(message: String): Unit = ()
  override def debug(message: String, throwable: Throwable): Unit = ()
  override def info(message: String): Unit = ()
  override def info(message: String, throwable: Throwable): Unit = ()
  override def warn(message: String): Unit = ()
  override def warn(message: String, throwable: Throwable): Unit = ()
  override def error(message: String): Unit = ()
  override def error(message: String, throwable: Throwable): Unit = ()
  override def withContext[A](context: Map[String, String])(body: => A): A = body
  override def traceContext: TraceContext = TraceContext.empty
  override def noTracingLogger: Logger = Logger(underlying)
  override def logger: TracedLogger =
    Logger.takingImplicit[TraceContext](underlying)(CanLogTraceContext)

}

object NoLogging extends NoLogging(properties = Map.empty, correlationId = None, traceId = None)
