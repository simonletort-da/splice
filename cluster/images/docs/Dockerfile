# Copyright (c) 2024 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

ARG base_version
FROM splice-app:${base_version} AS splice

ARG base_version
FROM splice-web-ui:${base_version}

ARG base_version
LABEL org.opencontainers.image.base.name="splice-web-ui:${base_version}"

COPY --from=splice app/splice-node/docs/html /usr/share/nginx/html/
COPY --from=splice app/LICENSE .
COPY script.js /tmpl/script.js.tmpl
COPY --chmod=500 docker-entrypoint.sh /custom-docker-entrypoint.sh
ENTRYPOINT ["/custom-docker-entrypoint.sh"]
