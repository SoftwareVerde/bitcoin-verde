# Install service
useradd -rs /bin/false bitcoin-verde
chown -R bitcoin-verde:bitcoin-verde /opt/bitcoin-verde
cp bitcoin-verde.service /etc/systemd/system/bitcoin-verde.service
systemctl daemon-reload
systemctl start bitcoin-verde
systemctl enable bitcoin-verde
systemctl status bitcoin-verde
