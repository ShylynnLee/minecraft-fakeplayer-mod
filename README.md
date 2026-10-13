# WorldEdit 假人（NeoForge 1.21.1）

阶段 1 版本：创建真正的 `ServerPlayer`，用于命令、传送、Tab 在线列表和 WorldEdit。

## 命令

```mcfunction
/fakeplayer spawn WETest 100 70 100
/fakeplayer remove WETest
/fakeplayer list
```

生成后：

- `/list` 和 Tab 列表显示假人
- `/tp WETest` 可以识别
- `@a` 和 `@e[type=minecraft:player]` 可以选中
- `/execute as WETest run //set ...` 使用真实玩家命令上下文
- 假人自动加入 OP，WorldEdit 权限可用
- 服务端重启后自动恢复
- 客户端无需安装

假人没有真实客户端连接，因此不会主动移动或接收客户端输入；本阶段不包含 AI 行为。

## 构建

```bash
cd /opt/Mc/fakeplayer-mod
./gradlew clean build
```

产物：

```text
build/libs/fakeplayer-1.0.0.jar
```
