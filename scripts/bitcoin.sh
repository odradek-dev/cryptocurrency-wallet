#!/usr/bin/env sh
# install bitcoin core
cd "$HOME"
curl https://bitcoincore.org/bin/bitcoin-core-23.0/bitcoin-23.0-x86_64-linux-gnu.tar.gz -o bitcoin.tar.gz
tar -xf bitcoin.tar.gz
rm -f bitcoin.tar.gz
mv "$HOME/bitcoin-23.0" "$HOME/bitcoin"
mkdir "$HOME/.bitcoin"

# install python
apt install python3

# install pip
curl https://bootstrap.pypa.io/get-pip.py -o get-pip.py
python3 get-pip.py
rm -f get-pip.py

conf_file_location=$HOME/.bitcoin/bitcoin.conf
service_file_location=/etc/systemd/system/bitcoin.service

# bitcoin service
cat > $service_file_location <<EOF
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

# bitcoin configuration file
cat > "$conf_file_location" <<EOF
# Generated by https://jlopp.github.io/bitcoin-core-config-generator/
chain=test
blocknotify=/usr/bin/python3 /root/bitcoin/services/block_notify.py %s
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
rpcauth=bitcoinUsername:6cf75c68c983ef03af2e080ea8555e5b\$b0c0412638fb43de95984391160a09514a0edd0c94549ef711b87b688c3fcd53
rpcport=18332
rpcallowip=0.0.0.0/0
walletnotify=/usr/bin/python3 /root/bitcoin/services/wallet_notify.py %s %w %h

# Options only for regtest
[regtest]
EOF

systemctl daemon-reload
systemctl start bitcon
systemctl enable bitcoin