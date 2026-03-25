#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
UBUNTU_APT_MIRROR="${CVECT_BOOTSTRAP_UBUNTU_APT_MIRROR:-}"
DOCKER_APT_REPO="${CVECT_BOOTSTRAP_DOCKER_APT_REPO:-https://download.docker.com/linux/ubuntu}"
DOCKER_GPG_URL="${CVECT_BOOTSTRAP_DOCKER_GPG_URL:-https://download.docker.com/linux/ubuntu/gpg}"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script as root: sudo bash scripts/bootstrap-ubuntu-docker.sh"
  exit 1
fi

if [[ ! -r /etc/os-release ]]; then
  echo "Cannot detect operating system"
  exit 1
fi

. /etc/os-release

if [[ "${ID:-}" != "ubuntu" ]]; then
  echo "This script currently supports Ubuntu only"
  exit 1
fi

ARCH="$(dpkg --print-architecture)"
CODENAME="${VERSION_CODENAME:-}"
TARGET_USER="${SUDO_USER:-}"

if [[ -z "${CODENAME}" ]]; then
  echo "Cannot detect Ubuntu codename"
  exit 1
fi

configure_ubuntu_apt_mirror() {
  if [[ -z "${UBUNTU_APT_MIRROR}" ]]; then
    return
  fi

  if [[ -f /etc/apt/sources.list.d/ubuntu.sources ]]; then
    sed -i \
      "s|http://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|https://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|http://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|https://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g" \
      /etc/apt/sources.list.d/ubuntu.sources
  elif [[ -f /etc/apt/sources.list ]]; then
    sed -i \
      "s|http://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|https://archive.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|http://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g;
       s|https://security.ubuntu.com/ubuntu/|${UBUNTU_APT_MIRROR}/|g" \
      /etc/apt/sources.list
  fi
}

configure_ubuntu_apt_mirror

echo "Using Ubuntu apt mirror: ${UBUNTU_APT_MIRROR:-default upstream}"
echo "Using Docker apt repo: ${DOCKER_APT_REPO}"
echo "Using Docker GPG URL: ${DOCKER_GPG_URL}"

apt-get update
apt-get install -y ca-certificates curl gnupg
install -m 0755 -d /etc/apt/keyrings

curl -fsSL "${DOCKER_GPG_URL}" -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc

cat >/etc/apt/sources.list.d/docker.list <<EOF
deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] ${DOCKER_APT_REPO} ${CODENAME} stable
EOF

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
systemctl enable --now docker

if [[ -n "${TARGET_USER}" ]]; then
  usermod -aG docker "${TARGET_USER}"
  echo "Added ${TARGET_USER} to the docker group"
fi

echo "Docker installation completed"
echo "Verify with: docker --version && docker compose version"
if [[ -n "${TARGET_USER}" ]]; then
  echo "Log out and back in once so the docker group takes effect for ${TARGET_USER}"
fi
echo
echo "Project root: ${ROOT_DIR}"
echo "Tip: set CVECT_BOOTSTRAP_UBUNTU_APT_MIRROR / CVECT_BOOTSTRAP_DOCKER_APT_REPO / CVECT_BOOTSTRAP_DOCKER_GPG_URL to use domestic mirrors."
