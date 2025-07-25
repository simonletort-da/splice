# Note that we don't currently support arm64 runners, so we build this only for amd64
FROM --platform=$BUILDPLATFORM ubuntu:24.04@sha256:440dcf6a5640b2ae5c77724e68787a906afb8ddee98bf86db94eea8528c2c076

LABEL org.opencontainers.image.base.name="ubuntu:24.04"
# TODO(#495): consider whether we can move some of the things installed here (and with pip below) into nix
RUN apt-get update && \
    apt-get install -y sudo git curl xz-utils pigz rsync jq unzip python3-pip && \
    rm -rf /var/lib/apt/lists/*

RUN groupadd --gid=1002 ci && \
    mkdir /github && \
    useradd --uid=1001 --gid=ci --home-dir /github/home --create-home ci && \
    echo 'ci ALL=NOPASSWD: ALL' >> /etc/sudoers.d/50-ci && \
    echo 'Defaults    env_keep += "DEBIAN_FRONTEND"' >> /etc/sudoers.d/env_keep && \
    sudo -u ci mkdir /github/home/project && \
    sudo -u ci mkdir /github/home/bin && \
    sudo -u ci mkdir -p /github/home/.local/bin && \
    sudo chown -R ci:ci /github/home

RUN sudo pip3 install \
    GitPython gql humanize marshmallow-dataclass requests requests_toolbelt prometheus_client flask waitress json-logging \
    --break-system-packages

RUN sudo ln -s /usr/bin/python3 /usr/bin/python

USER ci
RUN whoami && \
	# opt-out of the new security feature, not needed in a CI environment
	git config --global --add safe.directory '*'

ENV COURSIER_CACHE=/cache/coursier
ENV CI=true

COPY target/LICENSE .
COPY target/gha-runner-rpc.py .

WORKDIR /github/home/project
