#!/usr/bin/env bash
set -x
if [ -z "${SCP_SECRET}" ]; then
  echo "No SCP_SECRET found."
  exit 1;
fi

eval "$(ssh-agent -s)"
echo "${SCP_SECRET}" | base64 -di > .github/id_rsa
chmod 600 .github/id_rsa
ssh-add .github/id_rsa
rm .github/id_rsa
export RSYNC_RSH="ssh -o UserKnownHostsFile=docs/bin/gustav_known_hosts.txt "
