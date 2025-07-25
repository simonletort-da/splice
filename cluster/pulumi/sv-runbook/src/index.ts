// Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0
import { Auth0ClientType, Auth0Fetch, getAuth0Config } from 'splice-pulumi-common';
import { configForSv, svRunbookConfig } from 'splice-pulumi-common-sv';

import { installNode } from './installNode';
import {
  DISABLE_ONBOARDING_PARTICIPANT_PROMOTION_DELAY,
  getValidator1PartyId,
  SV_BENEFICIARY_VALIDATOR1,
} from './utils';

async function auth0CacheAndInstallNode(auth0Fetch: Auth0Fetch) {
  await auth0Fetch.loadAuth0Cache();

  const svAppConfig = {
    onboardingName: svRunbookConfig.onboardingName,
    disableOnboardingParticipantPromotionDelay: DISABLE_ONBOARDING_PARTICIPANT_PROMOTION_DELAY,
    externalGovernanceKey: configForSv(svRunbookConfig.nodeName)?.participant?.kms ? true : false,
  };
  const validatorAppConfig = {
    walletUserName: svRunbookConfig.validatorWalletUser!,
  };

  const resolveValidator1PartyId = SV_BENEFICIARY_VALIDATOR1 ? getValidator1PartyId : undefined;

  await installNode(
    auth0Fetch,
    svRunbookConfig.nodeName,
    svAppConfig,
    validatorAppConfig,
    resolveValidator1PartyId
  );

  await auth0Fetch.saveAuth0Cache();
}

async function main() {
  const auth0FetchOutput = getAuth0Config(Auth0ClientType.RUNBOOK);

  auth0FetchOutput.apply(auth0Fetch => {
    // eslint-disable-next-line @typescript-eslint/no-floating-promises
    auth0CacheAndInstallNode(auth0Fetch);
  });
}

// eslint-disable-next-line @typescript-eslint/no-floating-promises
main();
