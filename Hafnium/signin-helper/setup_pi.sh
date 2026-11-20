#!/usr/bin/env bash
# Installs the Hafnium sign-in helper on a Raspberry Pi (or any Linux computer) and
# starts it now and on every boot. Run it from the folder that has hafnium_signin_helper.py:
#   bash setup_pi.sh
set -euo pipefail
DIR="$HOME/hafnium-helper"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$DIR"
cp "$HERE/hafnium_signin_helper.py" "$DIR/"
python3 -m venv "$DIR/venv"
"$DIR/venv/bin/pip" install --quiet --upgrade websockets

if [ ! -f "$DIR/secret.env" ]; then
  SECRET="$(python3 -c 'import secrets; print(secrets.token_hex(24))')"
  umask 077
  echo "HAFNIUM_VERIFIER_SECRET=$SECRET" > "$DIR/secret.env"
  echo
  echo "Your new secret is below. Add it to the Cloudflare Worker as a secret named VERIFIER_SECRET."
  echo "Don't share it or paste it into chats."
  echo
  echo "    $SECRET"
  echo
else
  echo "Keeping the secret already in $DIR/secret.env."
fi

sudo tee /etc/systemd/system/hafnium-helper.service > /dev/null <<UNIT
[Unit]
Description=Hafnium sign-in helper
After=network-online.target
Wants=network-online.target

[Service]
User=$USER
EnvironmentFile=$DIR/secret.env
ExecStart=$DIR/venv/bin/python -I $DIR/hafnium_signin_helper.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable --now hafnium-helper
sudo systemctl restart hafnium-helper
echo "The helper is running. To watch it: journalctl -u hafnium-helper -f"
