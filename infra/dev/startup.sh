#!/bin/bash
yum update -y
yum install -y git wget python3 python3-pip

# install bitcoin core
cd "$HOME"
curl https://bitcoincore.org/bin/bitcoin-core-24.0.1/bitcoin-24.0.1-x86_64-linux-gnu.tar.gz -o bitcoin.tar.gz
tar -xf bitcoin.tar.gz
rm -f bitcoin.tar.gz
mv "$HOME/bitcoin-24.0.1" "$HOME/bitcoin"
mkdir "$HOME/.bitcoin"

# install python
sudo yum install python3

conf_file_location="$HOME/.bitcoin/bitcoin.conf"
service_file_location="/etc/systemd/system/bitcoin.service"

bitcoin_services_base_path="$HOME/services"

mkdir "$bitcoin_services_base_path"

block_notify_file_location="$bitcoin_services_base_path/block_notify.py"
wallet_notify_file_location="$bitcoin_services_base_path/wallet_notify.py"
settings_file_location="$bitcoin_services_base_path/settings.py"

# bitcoin service
cat >$service_file_location <<EOF
[Unit]
Description=Bitcoin daemon
Documentation=https://github.com/bitcoin/bitcoin/blob/master/doc/init.md
After=network-online.target
Wants=network-online.target

[Service]
ExecStart=$HOME/bitcoin/bin/bitcoind -conf=$HOME/.bitcoin/bitcoin.conf
Type=forking
Restart=on-failure
TimeoutStartSec=infinity
TimeoutStopSec=600
User=root
PrivateTmp=true
NoNewPrivileges=true
PrivateDevices=true
MemoryDenyWriteExecute=true

[Install]
WantedBy=multi-user.target
EOF

cat >$block_notify_file_location <<EOF
import argparse
import json
import logging
import os
import random
import requests
import subprocess
import time
from threading import Thread

import settings

logging.basicConfig(filename=os.path.join(settings.LOG_PATH, "block-notify.log"),
                    filemode='a',
                    format='%(asctime)s,%(msecs)d %(name)s %(levelname)s %(message)s',
                    datefmt='%Y-%m-%d %H:%M:%S',
                    level=logging.DEBUG)

logger = logging.getLogger("odradekPaySocketServer")

parser = argparse.ArgumentParser(description='Block notify script')

parser.add_argument("blockhash", metavar='N', type=str, nargs='+',
                    help='new block hash')

args = parser.parse_args()
block_hashes = args.blockhash
block_height = None
block_hash = None

if block_hashes:
    block_hash = block_hashes[0]

script_id = random.randint(1000000, 1000000000)
logger.info(f"<======================== STARTING EXECUTING SCRIPT <{script_id}> =============================>\n\n\n\n")

if block_hash:
    block_data = subprocess.Popen(
        [f"{settings.COIN_NAME} -conf={settings.CONF_PATH} getblock {block_hash} 1"],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    block_json, err = block_data.communicate(b"")
    block_json = json.loads(block_json)
    block_height = block_json.get("height")
    logger.info(block_json)
    logger.info(f"BLOCK HEIGHT: {block_height}")

data_to_send = {"blockNumber": block_height, "coin": settings.COIN_TICKER}


def post_notification(notify_url):
    sleep_time = 30
    tries = 0
    while tries < 5:
        tries += 1
        request_url = notify_url
        try:
            response = requests.post(request_url, json=data_to_send)
        except Exception as _:
            logger.info(f"Occured exception while sending request : {request_url}")
            logger.info("Next request after 30 seconds")
            time.sleep(sleep_time)
            sleep_time += 30
        else:
            if int(response.status_code) == 200:
                logger.info(f"Sent webhook data to {request_url}")
                break
            else:
                logger.info(f"Handler didn't returned status code 200, reason : {response.content}")
                time.sleep(sleep_time)
                sleep_time += 30


for url in settings.NOTIFY_URLS:
    Thread(target=post_notification, args=(url,)).start()

logger.info(f"\n\n\n<======================== END EXECUTING SCRIPT <{script_id}>=============================>\n\n\n\n")

EOF

cat >$wallet_notify_file_location <<EOF
import argparse
import logging
import os
import random
import requests
import time
from threading import Thread

import settings

logging.basicConfig(filename=os.path.join(settings.LOG_PATH, "wallet-notify.log"),
                    filemode='a',
                    format='%(asctime)s,%(msecs)d %(name)s %(levelname)s %(message)s',
                    datefmt='%Y-%m-%d %H:%M:%S',
                    level=logging.DEBUG)

logger = logging.getLogger("odradekPaySocketServer")

parser = argparse.ArgumentParser(description='Wallet notify script')

parser.add_argument("txid", metavar='N', type=str, nargs='+', help='txid which changed')
parser.add_argument("wallet_name", metavar='N', type=str, nargs='+', help='wallet name')
parser.add_argument("block_height", metavar='N', type=str, nargs='+',
                    help="tranasction blockheigh -1 if transaction is not confirmed")

args = parser.parse_args()
txids = args.txid
wallet_names = args.wallet_name
block_heights = args.block_height
txid = None
wallet_name = None
block_height = None

if txids:
    txid = txids[0]
if wallet_names:
    wallet_name = wallet_names[0]
if block_heights:
    block_height = block_heights[0]

script_id = random.randint(1000000, 1000000000)
logger.info(f"<======================== STARTING EXECUTING SCRIPT <{script_id}> =============================>\n\n\n\n")

data_to_send = {"blockHeight": block_height, "walletName": wallet_name, "txId": txid, "coin": settings.COIN_TICKER}

def post_notification(notify_url):
    sleep_time = 30
    tries = 0
    while tries < 5:
        tries += 1
        request_url = notify_url
        try:
            response = requests.post(request_url, json=data_to_send)
        except Exception as _:
            logger.info(f"Occured exception while sending request : {request_url}")
            logger.info("Next request after 30 seconds")
            time.sleep(sleep_time)
            sleep_time += 30
        else:
            if int(response.status_code) == 200:
                logger.info(f"Sent webhook data to {request_url}")
                break
            else:
                logger.info(f"Handler didn't returned status code 200, reason : {response.content}")
                time.sleep(sleep_time)
                sleep_time += 30


for url in settings.WEBHOOK_HANDLER_URLS:
    Thread(target=post_notification, args=(url,)).start()

logger.info(f"\n\n\n<======================== END EXECUTING SCRIPT <{script_id}>=============================>\n\n\n\n")

EOF

cat >$settings_file_location <<EOF
import os

COIN_NAME = "$HOME/bitcoin/bin/bitcoin-cli"
COIN_TICKER = "tbtc"
CONF_PATH = "$HOME/.bitcoin/bitcoin.conf"

NOTIFY_URLS = [
    "https://webhook.site/3765ebf1-e3ee-40ff-a960-0b624515b526"
]

WEBHOOK_HANDLER_URLS = [
   "https://webhook.site/3765ebf1-e3ee-40ff-a960-0b624515b526"
]

LOG_PATH = os.path.dirname(os.path.realpath(__file__))

EOF

# bitcoin configuration file
cat >"$conf_file_location" <<EOF
# Generated by https://jlopp.github.io/bitcoin-core-config-generator/
chain=test
blocknotify=/usr/bin/python3 $bitcoin_services_base_path/block_notify.py %s
coinstatsindex=1
daemon=1
deprecatedrpc=accounts
deprecatedrpc=addwitnessaddress
deprecatedrpc=signrawtransaction
deprecatedrpc=validateaddress
server=1

# [Sections]
# Most options automatically apply to mainnet, testnet, and regtest networks.
# If you want to confine an option to just one network, you should add it in the relevant section.
# EXCEPTIONS: The options addnode, connect, port, bind, rpcport, rpcbind and wallet
# only apply to mainnet unless they appear in the appropriate section below.

# Options only for mainnet
[main]

# Options only for testnet
[test]
rest=1
rpcbind=0.0.0.0
rpcauth=${rpc_auth}
rpcport=18332
rpcallowip=0.0.0.0/0
walletnotify=/usr/bin/python3 $bitcoin_services_base_path/wallet_notify.py %s %w %h

# Options only for regtest
[regtest]
EOF

systemctl daemon-reload
systemctl start bitcoin
systemctl enable bitcoin
