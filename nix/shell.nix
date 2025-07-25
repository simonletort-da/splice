{ pkgs, x86Pkgs, npmPkgs, use_enterprise }:
let
  inherit (pkgs) stdenv fetchzip;
  sources = builtins.fromJSON (builtins.readFile ./canton-sources.json);
  cometbftDriverSources = builtins.fromJSON (builtins.readFile ./cometbft-driver-sources.json);
  damlCompilerSources = builtins.fromJSON (builtins.readFile ./daml-compiler-sources.json);

  # No macOS support for firefox
  linuxOnly = if stdenv.isDarwin then [ ] else with pkgs; [ firefox iproute2 rust-parallel util-linux ];

in pkgs.mkShell {
  PULUMI_SKIP_UPDATE_CHECK = 1;
  SSL_CERT_FILE = "${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt";
  packages = with pkgs; [

    # NOTE: please keep this list sorted for an easy overview and to avoid merge noise.
    istioctl
    actionlint
    ammonite
    auth0-cli
    bc
    cabal2nix
    canton
    circleci-cli
    curl
    docker
    evans
    geckodriver
    getopt
    gh
    git
    # Required for the runner-container-hooks submodule
    git-lfs
    git-search-replace
    (google-cloud-sdk.withExtraComponents ([google-cloud-sdk.components.gke-gcloud-auth-plugin ]))
    grpcurl
    daml2js
    hub # Github CLI for todo checker
    jq
    jsonnet
    k6
    k9s
    kubectl
    (wrapHelm kubernetes-helm { plugins = with pkgs.kubernetes-helmPlugins; [ helm-unittest ]; })
    lnav
    nix
    nodejs
    nodePackages.node2nix
    npmPkgs.syncpack
    openapi-generator-cli
    openjdk21
    pigz
    popeye
    postgresql_14
    pre-commit
    procps
    protobuf_25
    ps
    pulumi-bin
    python3
    python3Packages.aiohttp
    python3Packages.auth0-python
    python3Packages.colorlog
    python3Packages.pycryptodome
    (python3Packages.datadog.overrideAttrs (old: {
                                             doCheck = false;
                                             doInstallCheck = false;
                                     }))
    python3Packages.dockerfile-parse
    python3Packages.flask
    python3Packages.GitPython
    python3Packages.gql
    python3Packages.humanize
    python3Packages.json-logging
    python3Packages.jsonpickle
    python3Packages.kubernetes
    python3Packages.marshmallow-dataclass
    python3Packages.polib
    python3Packages.pydantic
    python3Packages.pygithub
    python3Packages.pyjwt
    python3Packages.pyyaml
    python3Packages.regex
    python3Packages.requests
    python3Packages.requests_toolbelt
    python3Packages.semver
    python3Packages.sphinx_rtd_theme
    python3Packages.sphinx-copybutton
    python3Packages.sphinxcontrib-openapi
    python3Packages.sphinx-autobuild
    python3Packages.waitress
    python3.pkgs.pip  # TODO(DACH-NY/canton-network-internal#565): Remove this once we switch to poetry
    python3.pkgs.sphinx-reredirects
    redocly
    ripgrep
    rsync
    sbt
    scala_2_13
    selenium-server-standalone
    shellcheck
    skopeo
    sphinx
    sphinx-lint
    tmux
    toxiproxy
    unzip
    which
    zip

    # Package required to install daml studio
    yq-go
  ] ++ linuxOnly;

  CANTON = "${pkgs.canton}";
  DAML_COMPILER_VERSION = "${damlCompilerSources.version}";
  SDK_VERSION = "${sources.tooling_sdk_version}";
  COMETBFT_RELEASE_VERSION = "${cometbftDriverSources.version}";
  COMETBFT_IMAGE_SHA256 = "${cometbftDriverSources.image_sha256}";
  COMETBFT_DRIVER = if use_enterprise then "${pkgs.cometbft_driver}" else "";
  PULUMI_HOME = "${pkgs.pulumi-bin}";
  IS_ENTERPRISE = if use_enterprise then "true" else "false";
  # Avoid sbt-assembly falling over. See https://github.com/sbt/sbt-assembly/issues/496
  LC_ALL = if stdenv.isDarwin then "" else "C.UTF-8";
  # Avoid "warning: setlocale: LC_ALL: cannot change locale (C.UTF-8)"
  # warnings in damlc.
  LOCALE_ARCHIVE_2_27 = if pkgs.stdenv.hostPlatform.libc == "glibc"
                        then "${pkgs.glibcLocales}/lib/locale/locale-archive"
                        else null;

  PROTOC = "${pkgs.protobuf_25}";

  PULUMI_VERSION="${pkgs.pulumi-bin.version}";
  GECKODRIVER="${pkgs.geckodriver}/bin/geckodriver";
}
