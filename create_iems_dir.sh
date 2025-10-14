#!/usr/bin/env bash
set -euo pipefail

# 可通过环境变量 OWNER 设置目录所有者（格式 user:group），默认不修改所有者
OWNER="${OWNER:-}"

APKS_DIR="/var/www/iems/storage/apks"
ICONS_DIR="/var/www/iems/storage/icons"
DIRS=( "$APKS_DIR" "$ICONS_DIR" )

create_with_sudo() {
  local dir="$1"
  if [ -d "$dir" ]; then
    echo "已存在: $dir"
    return 0
  fi

  if [ "$(id -u)" -ne 0 ]; then
    sudo mkdir -p "$dir"
    sudo chmod 0755 "$dir"
    if [ -n "$OWNER" ]; then sudo chown "$OWNER" "$dir"; fi
  else
    mkdir -p "$dir"
    chmod 0755 "$dir"
    if [ -n "$OWNER" ]; then chown "$OWNER" "$dir"; fi
  fi
  echo "已创建: $dir"
}

for d in "${DIRS[@]}"; do
  create_with_sudo "$d"
done

echo "完成。"