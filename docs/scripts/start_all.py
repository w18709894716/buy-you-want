#!/usr/bin/env python3
"""
BuyYouWant 微服务一键启动脚本
按依赖顺序启动 Java 后端服务和前端应用。
启动后进入交互模式，可输入命令管理单个服务。
"""

import subprocess
import sys
import os
import time
import glob
import argparse
import threading

# 项目根目录（脚本位于 docs/scripts/，需向上两级）
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# ===== 服务定义 =====
BATCH_1 = ["byw-gateway", "byw-auth"]
BATCH_2 = ["byw-user", "byw-product"]
BATCH_3 = ["byw-cart", "byw-order", "byw-pay",
           "byw-logistics", "byw-review", "byw-promotion", "byw-admin"]

ALL_JAVA_SERVICES = BATCH_1 + BATCH_2 + BATCH_3

PORTS = {
    "byw-gateway": 8080, "byw-auth": 8081, "byw-user": 8082,
    "byw-product": 8083, "byw-cart": 8084, "byw-order": 8085,
    "byw-pay": 8086, "byw-logistics": 8087, "byw-review": 8088,
    "byw-promotion": 8089, "byw-admin": 8090,
}

# 全局变量：存储正在运行的服务进程
running_services = {}  # {name: proc}


def ok(msg):
    print(f"  \033[32m[OK]\033[0m {msg}")


def warn(msg):
    print(f"  \033[33m[!!]\033[0m {msg}")


def info(msg):
    print(f"  \033[37m[..]\033[0m {msg}")


def stage(msg):
    print(f"\n\033[36m========== {msg} ==========\033[0m")


def start_java_service(name: str, register: bool = True):
    """在独立终端窗口中启动 java -jar（可看日志）"""
    target_dir = os.path.join(ROOT, name, "target")
    # 查找 {name}-*.jar（带版本号）
    jars = glob.glob(os.path.join(target_dir, f"{name}-*.jar"))
    # 排除 sources/javadoc 等附加 jar
    jars = [j for j in jars if not j.endswith("-sources.jar") and not j.endswith("-javadoc.jar")]
    if not jars:
        warn(f"{name}: jar 不存在 ({target_dir})，跳过")
        return None
    jar = jars[0]
    # 用 cmd /k 开独立窗口（/k 保持窗口不关闭，便于查看日志和错误）
    proc = subprocess.Popen(
        ["cmd", "/k", f"title {name} && java -jar {jar}"],
        cwd=os.path.dirname(jar),
        creationflags=subprocess.CREATE_NEW_CONSOLE,
    )
    ok(f"{name} :{PORTS[name]}  (pid={proc.pid})")
    if register:
        running_services[name] = proc
    return proc


def start_frontend(name: str, work_dir: str):
    """在独立窗口中启动前端 dev server"""
    proc = subprocess.Popen(
        ["cmd", "/c", f"title {name} && npm run dev"],
        cwd=work_dir,
        creationflags=subprocess.CREATE_NEW_CONSOLE,
    )
    ok(f"{name}  ->  {work_dir}")
    # 注册到 running_services，以便 shutdown 能通过 PID 停止
    running_services[name] = proc
    return proc


def stop_service(name: str) -> bool:
    """停止单个服务（优先通过 PID，备用通过窗口标题/端口查找）"""
    if name in running_services:
        proc = running_services[name]
        if proc and proc.pid:
            os.system(f"taskkill /PID {proc.pid} /F /T >nul 2>&1")
            del running_services[name]
            ok(f"{name} 已停止")
            return True
    # 备用：通过窗口标题查找
    pids = find_pids_by_window_title(name)
    if pids:
        for pid in pids:
            os.system(f"taskkill /PID {pid} /F /T >nul 2>&1")
        ok(f"{name} 已停止 (pid={','.join(map(str, pids))})")
        if name in running_services:
            del running_services[name]
        return True
    # 前端备用：通过端口查找
    frontend_ports = {"byw-web": 3000, "byw-admin-web": 5173}
    if name in frontend_ports:
        port_pids = find_pids_by_port(frontend_ports[name])
        for pid in port_pids:
            os.system(f"taskkill /PID {pid} /F /T >nul 2>&1")
        if port_pids:
            ok(f"{name} 已停止 (端口 pid={','.join(map(str, port_pids))})")
            return True
    warn(f"{name} 未运行")
    return False


def find_pids_by_window_title(title: str) -> list:
    """通过窗口标题查找进程 PID"""
    pids = []
    try:
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


def find_pids_by_port(port: int) -> list:
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


def build_service(name: str) -> bool:
    """编译打包单个服务模块"""
    info(f"编译 {name} ...")
    result = subprocess.run(
        f"mvn package -pl {name} -am -DskipTests -q",
        cwd=ROOT,
        shell=True,
    )
    if result.returncode != 0:
        warn(f"{name} 编译失败！")
        return False
    ok(f"{name} 编译完成")
    return True


def restart_service(name: str, skip_build: bool = False):
    """重启单个服务（默认先编译打包）"""
    if name not in ALL_JAVA_SERVICES:
        warn(f"未知服务: {name}")
        return
    print(f"\n  --- 重启 {name} ---")
    stop_service(name)
    time.sleep(1)
    if not skip_build:
        if not build_service(name):
            return
    start_java_service(name)


def show_status():
    """显示服务状态"""
    print("\n  \033[36m=== 服务状态 ===\033[0m")
    # 后端服务
    for name in ALL_JAVA_SERVICES:
        port = PORTS.get(name, '?')
        if name in running_services:
            proc = running_services[name]
            status = "\033[32m运行中\033[0m" if proc.poll() is None else "\033[31m已退出\033[0m"
        else:
            pids = find_pids_by_window_title(name)
            status = "\033[33m外部运行\033[0m" if pids else "\033[37m未启动\033[0m"
        print(f"    {name:18} :{str(port):5}  {status}")
    # 前端
    frontend = [("byw-web", 3000), ("byw-admin-web", 5173)]
    for name, port in frontend:
        if name in running_services:
            proc = running_services[name]
            status = "\033[32m运行中\033[0m" if proc.poll() is None else "\033[31m已退出\033[0m"
        else:
            pids = find_pids_by_port(port)
            status = "\033[33m外部运行\033[0m" if pids else "\033[37m未启动\033[0m"
        print(f"    {name:18} :{str(port):5}  {status}")
    print()


def print_help():
    """打印帮助信息"""
    print("""
  \033[36m可用命令:\033[0m
    \033[1mrestart <服务名> [--skip-build]\033[0m   重启指定服务（默认先编译打包）
    \033[1mstop <服务名>\033[0m                    停止指定服务
    \033[1mstart <服务名> [--skip-build]\033[0m    启动指定服务（默认先编译打包）
    \033[1mstatus\033[0m                           显示所有服务状态
    \033[1mlist\033[0m                             列出所有服务名
    \033[1mhelp\033[0m                             显示此帮助信息
    \033[1mquit\033[0m / \033[1mexit\033[0m                      退出（不停止服务）
    \033[1mshutdown\033[0m                         停止所有服务（后端 + 前端）并退出

  \033[36m示例:\033[0m
    restart byw-order              编译并重启订单服务
    restart byw-order --skip-build 跳过编译直接重启

  \033[36m服务名列表:\033[0m
    """ + ", ".join(ALL_JAVA_SERVICES))


def launch_batch(names: list, label: str) -> list:
    print(f"\n  --- {label} ---")
    procs = []
    for n in names:
        p = start_java_service(n)
        if p:
            procs.append(p)
    return procs


def main():
    parser = argparse.ArgumentParser(description="BuyYouWant 一键启动")
    parser.add_argument("--skip-build", action="store_true", help="跳过 Maven 构建")
    parser.add_argument("--skip-frontend", action="store_true", help="跳过前端启动")
    args = parser.parse_args()

    # ===== 阶段 1: Maven 构建 =====
    if not args.skip_build:
        stage("阶段 1/3 - Maven 构建")
        info("执行 mvn clean package -DskipTests ...")
        result = subprocess.run(
            "mvn clean package -DskipTests -q",
            cwd=ROOT,
            shell=True,
        )
        if result.returncode != 0:
            print("\n\033[31m  Maven 构建失败！请检查编译错误。\033[0m")
            sys.exit(1)
        ok("构建完成")
    else:
        stage("阶段 1/3 - Maven 构建 (已跳过)")

    # ===== 阶段 2: Java 微服务 =====
    stage("阶段 2/3 - 启动 Java 微服务")
    all_procs = []  # 存储所有进程对象，用于停止时关闭窗口

    all_procs.extend(launch_batch(BATCH_1, "第 1 批: 网关 + 认证"))
    time.sleep(5)
    all_procs.extend(launch_batch(BATCH_2, "第 2 批: 用户 + 商品"))
    time.sleep(5)
    all_procs.extend(launch_batch(BATCH_3, "第 3 批: 业务服务"))

    # ===== 阶段 3: 前端 =====
    if not args.skip_frontend:
        stage("阶段 3/3 - 启动前端")
        web_dir = os.path.join(ROOT, "byw-frontend", "byw-web")
        admin_dir = os.path.join(ROOT, "byw-frontend", "byw-admin-web")
        all_procs.append(start_frontend("byw-web", web_dir))
        all_procs.append(start_frontend("byw-admin-web", admin_dir))
    else:
        stage("阶段 3/3 - 前端 (已跳过)")

    # ===== 汇总 =====
    print()
    print("\033[32m============================================\033[0m")
    print("\033[32m  所有服务启动完成！\033[0m")
    print("\033[32m============================================\033[0m")
    print()
    print("  \033[33m微服务:\033[0m")
    print("    Gateway  :8080    Auth      :8081    User     :8082")
    print("    Product  :8083    Cart      :8084    Order    :8085")
    print("    Pay      :8086    Logistics :8087    Review   :8088")
    print("    Promotion:8089    Admin     :8090")
    print()
    print("  \033[33m前端:\033[0m")
    print("    byw-web       http://localhost:3000")
    print("    byw-admin-web http://localhost:5173")
    print()
    print("  \033[37m输入 'help' 查看可用命令\033[0m")
    print()

    # ===== 交互模式 =====
    interactive_mode(all_procs)


def interactive_mode(all_procs: list):
    """交互式命令模式"""
    print("\033[36m  进入交互模式 (输入 help 查看帮助)\033[0m")
    print("-" * 50)

    while True:
        try:
            cmd = input("\n\033[35mbyw>\033[0m ").strip().lower()
            if not cmd:
                continue

            parts = cmd.split()
            action = parts[0]

            if action in ("quit", "exit", "q"):
                print("\n  退出交互模式，服务继续运行...")
                print("  \033[37m停止服务: python docs/scripts/stop_all.py\033[0m")
                break

            elif action == "shutdown":
                print("\n  正在停止所有服务...")
                for name in list(running_services.keys()):
                    stop_service(name)
                # 前端备用停止：通过端口查找（以防 npm 修改窗口标题导致未匹配）
                for port, name in [(3000, "byw-web"), (5173, "byw-admin-web")]:
                    if name not in running_services:
                        pids = find_pids_by_port(port)
                        for pid in pids:
                            os.system(f"taskkill /PID {pid} /F /T >nul 2>&1")
                        if pids:
                            ok(f"{name} 已停止 (端口 pid={','.join(map(str, pids))})")
                print("  已停止所有服务。")
                break

            elif action == "help":
                print_help()

            elif action == "status":
                show_status()

            elif action == "list":
                print("\n  \033[36m后端服务:\033[0m")
                for name in ALL_JAVA_SERVICES:
                    print(f"    {name} (:{PORTS[name]})")
                print("\n  \033[36m前端:\033[0m")
                print("    byw-web (:3000)")
                print("    byw-admin-web (:5173)")
                print()

            elif action == "restart" and len(parts) >= 2:
                skip = "--skip-build" in parts
                restart_service(parts[1], skip_build=skip)

            elif action == "stop" and len(parts) >= 2:
                stop_service(parts[1])

            elif action == "start" and len(parts) >= 2:
                name = parts[1]
                skip = "--skip-build" in parts
                if name not in ALL_JAVA_SERVICES:
                    warn(f"未知服务: {name}")
                else:
                    if not skip:
                        if not build_service(name):
                            continue
                    start_java_service(name)

            else:
                warn(f"未知命令: {cmd}")
                print("  输入 'help' 查看可用命令")

        except KeyboardInterrupt:
            print("\n\n  按 Ctrl+C 再次退出，或输入 'shutdown' 停止所有服务")
        except EOFError:
            break


if __name__ == "__main__":
    main()
