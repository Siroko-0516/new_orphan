#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export UV_CACHE_DIR="${UV_CACHE_DIR:-/tmp/giljabi-uv-cache}"
export UV_LINK_MODE=copy
if [ ! -d .venv ]; then uv venv .venv; fi
uv pip install --python .venv/bin/python 'torch==2.15.0' 'torchvision==0.29.2' --index-url https://download.pytorch.org/whl/cpu
uv pip install --python .venv/bin/python -r requirements.txt
mkdir -p models/config
if [ ! -f models/yolo11n.pt ]; then
  curl --fail --location --retry 2 https://github.com/ultralytics/assets/releases/download/v8.3.0/yolo11n.pt --output models/yolo11n.pt.download
  echo '0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1  models/yolo11n.pt.download' | sha256sum --check
  mv models/yolo11n.pt.download models/yolo11n.pt
fi
echo '0ebbc80d4a7680d14987a577cd21342b65ecfd94632bd9a8da63ae6417644ee1  models/yolo11n.pt' | sha256sum --check
