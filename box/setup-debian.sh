#!/usr/bin/env bash
# OpenPack box setup for Debian / Raspberry Pi OS (Bookworm).
# Turns a fresh device into an offline Wi-Fi box serving a built pack.
#
# STATUS: written to spec, NOT yet tested on real hardware. Test on a spare Pi first.
#
# Usage (as root):
#   sudo ./setup-debian.sh /path/to/dist/<pack-folder> [SSID]
set -euo pipefail

PACK_DIR="${1:?usage: $0 <built-pack-dir> [ssid]}"
SSID="${2:-OpenPack}"
WWW=/var/www/openpack
IFACE="${OPENPACK_IFACE:-wlan0}"
GATEWAY=10.42.0.1

[[ $EUID -eq 0 ]] || { echo "run as root"; exit 1; }
[[ -f "$PACK_DIR/manifest.json" ]] || { echo "not a built pack: $PACK_DIR"; exit 1; }

echo "[1/7] Installing packages"
DEBIAN_FRONTEND=noninteractive apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y hostapd dnsmasq nginx rfkill kiwix-tools nodejs

echo "[2/7] Copying pack to $WWW"
mkdir -p "$WWW"
rsync -a --delete "$PACK_DIR"/ "$WWW"/ 2>/dev/null || { rm -rf "${WWW:?}"/*; cp -a "$PACK_DIR"/. "$WWW"/; }

echo "[3/7] Static IP for $IFACE"
systemctl stop NetworkManager 2>/dev/null || true
systemctl disable NetworkManager 2>/dev/null || true
cat > /etc/network/interfaces.d/openpack <<EOF
auto $IFACE
iface $IFACE inet static
    address $GATEWAY
    netmask 255.255.255.0
EOF

echo "[4/7] Access point (open network, no passphrase by design: no accounts, no logs)"
rfkill unblock wlan || true
cat > /etc/hostapd/hostapd.conf <<EOF
interface=$IFACE
driver=nl80211
ssid=$SSID
hw_mode=g
channel=6
ieee80211n=1
wmm_enabled=1
auth_algs=1
ignore_broadcast_ssid=0
EOF
sed -i 's|^#\?DAEMON_CONF=.*|DAEMON_CONF="/etc/hostapd/hostapd.conf"|' /etc/default/hostapd

echo "[5/7] DHCP + captive DNS (every domain resolves to the box)"
cat > /etc/dnsmasq.d/openpack.conf <<EOF
interface=$IFACE
bind-interfaces
dhcp-range=10.42.0.10,10.42.0.250,255.255.255.0,12h
dhcp-option=option:router,$GATEWAY
dhcp-option=option:dns-server,$GATEWAY
address=/#/$GATEWAY
no-resolv
log-queries=no
EOF

echo "[6/7] Web server"
cat > /etc/nginx/sites-available/openpack <<EOF
server {
    listen 80 default_server;
    server_name _;
    root $WWW;
    index index.html;
    access_log off;                     # no logs: we do not record who connects
    charset utf-8;
    types { text/markdown md; application/vnd.android.package-archive apk; }

    # Captive-portal probes used by Android / iOS / Windows: send them to the portal.
    location = /generate_204      { return 302 http://$GATEWAY/; }
    location = /hotspot-detect.html { return 302 http://$GATEWAY/; }
    location = /connecttest.txt   { return 302 http://$GATEWAY/; }

    # Kiwix archives (offline Wikipedia etc.), served by kiwix-serve on localhost.
    location /kiwix/ {
        proxy_pass http://127.0.0.1:8081/kiwix/;
        proxy_set_header Host \$host;
    }

    location / { try_files \$uri \$uri/ /index.html; }
}
EOF
ln -sf /etc/nginx/sites-available/openpack /etc/nginx/sites-enabled/openpack
rm -f /etc/nginx/sites-enabled/default

echo "[7/7] Kiwix service (offline Wikipedia and other ZIM archives)"
install -m 0755 "$(dirname "$0")/kiwix-serve.sh" /usr/local/bin/openpack-kiwix-serve
cat > /etc/systemd/system/openpack-kiwix.service <<UNIT
[Unit]
Description=OpenPack Kiwix server
After=network.target

[Service]
ExecStart=/usr/local/bin/openpack-kiwix-serve
Restart=on-failure

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable openpack-kiwix

systemctl unmask hostapd
systemctl enable --now hostapd dnsmasq nginx
systemctl restart networking 2>/dev/null || ifup "$IFACE" || true
systemctl restart hostapd dnsmasq nginx openpack-kiwix

echo
echo "Done. Join Wi-Fi \"$SSID\" on a phone; the portal should open automatically."
echo "Update the pack later with:  sudo ./update-pack.sh /path/to/new/dist/<pack-folder>"
