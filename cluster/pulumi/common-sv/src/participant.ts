// Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
import * as k8s from '@pulumi/kubernetes';
import * as pulumi from '@pulumi/pulumi';
import {
  Auth0Config,
  ChartValues,
  DEFAULT_AUDIENCE,
  DomainMigrationIndex,
  ExactNamespace,
  getParticipantKmsHelmResources,
  InstalledHelmChart,
  installSpliceHelmChart,
  jmxOptions,
  loadYamlFromFile,
  SPLICE_ROOT,
  SpliceCustomResourceOptions,
} from 'splice-pulumi-common';
import { CnChartVersion } from 'splice-pulumi-common/src/artifacts';
import { Postgres } from 'splice-pulumi-common/src/postgres';

import { SingleSvConfiguration } from './singleSvConfig';

export interface SvParticipant {
  readonly asDependencies: pulumi.Resource[];
  readonly internalClusterAddress: pulumi.Output<string>;
}

export function installSvParticipant(
  xns: ExactNamespace,
  svConfig: SingleSvConfiguration,
  migrationId: DomainMigrationIndex,
  auth0Config: Auth0Config,
  isActive: boolean,
  db: Postgres,
  version: CnChartVersion,
  onboardingName: string,
  participantAdminUserNameFrom?: k8s.types.input.core.v1.EnvVarSource,
  imagePullServiceAccountName?: string,
  customOptions?: SpliceCustomResourceOptions
): InstalledHelmChart {
  const name = `participant-${migrationId}`;
  const participantValues: ChartValues = {
    ...loadYamlFromFile(
      `${SPLICE_ROOT}/apps/app/src/pack/examples/sv-helm/participant-values.yaml`,
      {
        MIGRATION_ID: migrationId.toString(),
        OIDC_AUTHORITY_URL: auth0Config.auth0Domain,
      }
    ),
  };

  const { kmsValues, kmsDependencies } = svConfig.participant?.kms
    ? getParticipantKmsHelmResources(xns, svConfig.participant.kms, migrationId)
    : { kmsValues: {}, kmsDependencies: [] };

  const participantValuesWithOverwrites: ChartValues = {
    ...participantValues,
    ...{
      persistence: {
        ...participantValues.persistence,
        postgresName: db.instanceName,
        host: db.address,
        secretName: db.secretName,
      },
    },
    auth: {
      ...participantValues.auth,
      targetAudience: auth0Config.appToApiAudience['participant'] || DEFAULT_AUDIENCE,
      jwksUrl: `https://${auth0Config.auth0Domain}/.well-known/jwks.json`,
    },
    ...kmsValues,
  };

  return installSpliceHelmChart(
    xns,
    name,
    'splice-participant',
    {
      ...participantValuesWithOverwrites,
      logLevel: svConfig.logging?.cantonLogLevel,
      participantAdminUserNameFrom,
      metrics: {
        enable: true,
        migration: {
          id: migrationId,
        },
      },
      additionalJvmOptions: jmxOptions(),
      enablePostgresMetrics: true,
      serviceAccountName: imagePullServiceAccountName,
      resources: {
        limits: {
          memory: '18Gi',
        },
        requests: {
          memory: '12Gi',
        },
      },
    },
    version,
    {
      ...(customOptions || {}),
      dependsOn: (customOptions?.dependsOn || []).concat([db]).concat(kmsDependencies),
    }
  );
}
