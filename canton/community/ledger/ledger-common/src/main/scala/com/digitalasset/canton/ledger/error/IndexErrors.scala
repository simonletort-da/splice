// Copyright (c) 2025 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.canton.ledger.error

import com.digitalasset.base.error.ErrorCode.LoggedApiException
import com.digitalasset.base.error.{
  DamlErrorWithDefiniteAnswer,
  ErrorCategory,
  ErrorCode,
  ErrorGroup,
  Explanation,
  Resolution,
}
import com.digitalasset.canton.ledger.error.ParticipantErrorGroup.IndexErrorGroup
import com.digitalasset.canton.logging.ErrorLoggingContext

@Explanation("Errors raised by the Participant Index persistence layer.")
object IndexErrors extends IndexErrorGroup {
  object DatabaseErrors extends ErrorGroup() {
    @Explanation(
      "This error occurs if a transient error arises when executing a query against the index database."
    )
    @Resolution("Re-submit the request.")
    object SqlTransientError
        extends ErrorCode(
          id = "INDEX_DB_SQL_TRANSIENT_ERROR",
          ErrorCategory.TransientServerFailure,
        ) {
      final case class Reject(throwable: Throwable)(implicit
          val loggingContext: ErrorLoggingContext
      ) extends DbError(
            cause =
              s"Processing the request failed due to a transient database error: ${throwable.getMessage}",
            throwableO = Some(throwable),
          )
    }

    @Explanation(
      "This error occurs if a non-transient error arises when executing a query against the index database."
    )
    @Resolution("Contact the participant operator.")
    object SqlNonTransientError
        extends ErrorCode(
          id = "INDEX_DB_SQL_NON_TRANSIENT_ERROR",
          ErrorCategory.SystemInternalAssumptionViolated,
        ) {
      final case class Reject(throwable: Throwable)(implicit
          val loggingContext: ErrorLoggingContext
      ) extends DbError(
            cause =
              s"Processing the request failed due to a non-transient database error: ${throwable.getMessage}",
            throwableO = Some(throwable),
          )
    }
  }

  // Decorator that returns a specialized StatusRuntimeException (IndexDbException)
  // that can be used for precise matching of persistence exceptions (e.g. for index initialization failures that need retrying).
  // Without this specialization, internal errors just appear as StatusRuntimeExceptions (see INDEX_DB_SQL_NON_TRANSIENT_ERROR)
  // without any marker, impeding us to assert whether they are emitted by the persistence layer or not.
  abstract class DbError(
      override val cause: String,
      override val throwableO: Option[Throwable] = None,
  )(implicit
      code: ErrorCode,
      loggingContext: ErrorLoggingContext,
  ) extends DamlErrorWithDefiniteAnswer(cause, throwableO) {

    override def asGrpcError: IndexDbException = {
      val err = super.asGrpcError
      IndexDbException(err.getStatus, err.getTrailers)
    }
  }

  final case class IndexDbException(status: io.grpc.Status, metadata: io.grpc.Metadata)
      extends LoggedApiException(status, metadata)

}
