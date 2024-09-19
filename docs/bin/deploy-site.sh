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
echo "${SCP_SECRET}" | base64 -d > /tmp/id_rsa
chmod 600 /tmp/id_rsa
scp -i /tmp/id_rsa -o UserKnownHostsFile=docs/bin/gustav_known_hosts.txt -r target/* ${TARGET}
rm /tmp/id_rsa
