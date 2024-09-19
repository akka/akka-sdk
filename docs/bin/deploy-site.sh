#!/usr/bin/env bash
if [ -z ${SCP_SECRET} ]; then
  echo "No SCP_SECRET found."
  exit 1;
fi

TARGET=$1
if [ -z ${TARGET} ]; then
  echo "No scp target parameter set. (e.g. akkarepo@gustav.akka.io:www/snapshots/ )"
  exit 1;
fi

make local
eval "$(ssh-agent -s)"
echo "${SCP_SECRET}" | base64 -di > .github/id_rsa
chmod 600 .github/id_rsa
export RSYNC_RSH="ssh -o UserKnownHostsFile=docs/bin/gustav_known_hosts.txt "
rsync -azP target/site/akka-documentation/* ${TARGET}
rm .github/id_rsa
