#!/usr/bin/env python3
"""
BuyYouWant 微服务一键停止脚本
停止所有 Java 后端服务和前端 dev server，同时关闭 CMD 窗口。
"""

import subprocess


def ok(msg):
    print(f"  \033[32m[OK]\033[0m {msg}")


def skip(msg):
    print(f"  \033[37m[--]\033[0m {msg}")


def find_pids_by_window_title(title: str) -> list[int]:
    """通过窗口标题查找进程 PID（用于查找 CMD 窗口进程）"""
    pids = []
    try:
        # 使用 tasklist 按窗口标题筛选
        output = subprocess.check_output(
            f'tasklist /FI "WINDOWTITLE eq {title}*" /FO CSV /NH',
            shell=True, text=True, stderr=subprocess.DEVNULL,
        )
        for line in output.strip().splitlines():
            if title in line:
                parts = line.split(',')
                if len(parts) >= 2:
                    try:
                        pid = int(parts[1].strip('"'))
                        pids.append(pid)
                    except ValueError:
                        pass
    except Exception:
        pass
    return pids


def find_pids_by_jar(name: str) -> list[int]:
    """通过 jps 查找包含指定 jar 名的 java 进程（备用方案）"""
    pids = []
    try:
        output = subprocess.check_output(
            ["jps", "-l"], text=True, stderr=subprocess.DEVNULL,
        )
        for line in output.strip().splitlines():
            parts = line.split(None, 1)
            if len(parts) == 2 and name in parts[1]:
                pids.append(int(parts[0]))
    except Exception:
        pass
    return pids


def find_pids_by_port(port: int) -> list[int]:
    """通过 netstat 查找占用指定端口的进程"""
    pids = []
    try:
        output = subprocess.check_output(
            ["netstat", "-ano"], text=True, stderr=subprocess.DEVNULL,
        )
        for line in output.splitlines():
            if f":{port} " in line and "LISTENING" in line:
                parts = line.split()
                pid = int(parts[-1])
                if pid not in pids:
                    pids.append(pid)
    except Exception:
        pass
    return pids


def kill_pid_tree(pid: int):
    """终止进程树（包括子进程）"""
    subprocess.run(
        ["taskkill", "/PID", str(pid), "/F", "/T"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
    )


def main():
    import argparse
    parser = argparse.ArgumentParser(description="BuyYouWant 一键停止")
    args = parser.parse_args()

    services = [
        "byw-gateway", "byw-auth", "byw-user", "byw-product",
        "byw-cart", "byw-order", "byw-pay", "byw-logistics",
        "byw-review", "byw-promotion", "byw-admin",
    ]

    # ===== 停止 Java 微服务（通过窗口标题查找 CMD 窗口）=====
    print("\n\033[36m========== 停止 Java 微服务 ==========\033[0m")
    killed = 0
    for name in services:
        # 优先通过窗口标题查找 CMD 进程
        pids = find_pids_by_window_title(name)
        if pids:
            for pid in pids:
                kill_pid_tree(pid)
            ok(f"{name}  (窗口 pid={','.join(map(str, pids))})")
            killed += 1
        else:
            # 备用方案：通过 jps 查找 Java 进程
            java_pids = find_pids_by_jar(name)
            if java_pids:
                for pid in java_pids:
                    kill_pid_tree(pid)
                ok(f"{name}  (java pid={','.join(map(str, java_pids))})")
                killed += 1
            else:
                skip(f"{name}  (未运行)")

    # ===== 停止前端（通过端口查找）=====
    print("\n\033[36m========== 停止前端 ==========\033[0m")
    frontend_killed = 0
    for port, name in [(3000, "byw-web :3000"), (5173, "byw-admin-web :5173")]:
        # 先尝试通过窗口标题查找
        title_name = name.split()[0]  # "byw-web" or "byw-admin-web"
        pids = find_pids_by_window_title(title_name)
        if pids:
            for pid in pids:
                kill_pid_tree(pid)
            ok(f"{name}  (窗口 pid={','.join(map(str, pids))})")
            frontend_killed += 1
        else:
            # 备用方案：通过端口查找
            port_pids = find_pids_by_port(port)
            if port_pids:
                for pid in port_pids:
                    kill_pid_tree(pid)
                ok(f"{name}  (端口 pid={','.join(map(str, port_pids))})")
                frontend_killed += 1
            else:
                skip(f"{name}  (未运行)")

    # ===== 汇总 =====
    print(f"\n\033[32m========== 完成 ==========\033[0m")
    print(f"  已停止 {killed} 个微服务 + {frontend_killed} 个前端进程")
    print("  \033[37m中间件未受影响\033[0m")
    print()


if __name__ == "__main__":
    main()
