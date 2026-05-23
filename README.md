# Touch Diagnostic

一个原生 Android 触控屏诊断 App，用来快速判断设备是否存在触控死区、断触、跳点、多点触控异常，或者屏幕边缘/角落触控不稳定的问题。

项目目录：

`/Users/jiangdk/code/company/touch_diagnostic`

## 适用场景

- 机器人设备现场排查触控屏是否有问题
- 区分“屏幕触控异常”和“网页 / WebView 卡顿”
- 给测试、售后、现场实施一个简单直接的触控验证工具

## 当前功能

- 全屏触控画线
- 多点触控，按手指区分颜色
- 网格覆盖显示，方便定位局部死区
- 实时显示触点数、笔画数、事件数
- 实时显示最后一次触摸事件类型和坐标
- 统计最大跳点距离，辅助判断触控是否抖动或跳变
- 支持清屏
- 支持显示 / 隐藏网格

## 如何判断屏幕是否有问题

用这个 App 做测试时，重点看下面几种现象：

- 某一块区域反复画不出来：大概率有死区
- 线条中途断开：大概率有断触
- 手指平滑移动，但线条突然跳很远：可能有跳点或驱动异常
- 边缘、四角明显比中间更难画：可能是边缘区域触控不稳定
- 两根或多根手指同时移动时，某根线经常丢失：可能是多点触控异常

如果这个 App 里画线顺畅、坐标稳定，但你们自己的业务页面还是卡，那么更应该优先排查网页、WebView 或业务逻辑性能，而不是屏幕本身。

## 运行环境

- JDK 17
- Android SDK
- Android Studio 或命令行 Gradle
- 一台可安装 APK 的 Android 设备

## 工程信息

- 项目目录名：`touch_diagnostic`
- App 显示名：`Touch Diagnostic`
- 包名：`com.company.touchdiagnostic`
- 最低支持版本：Android 6.0（API 23）

## 构建方式

### 1. 配置 Android SDK

在项目根目录创建 `local.properties`，内容如下：

```properties
sdk.dir=/Users/你的用户名/Library/Android/sdk
```

也可以参考仓库里的 `local.properties.example`。

### 2. 构建 Debug APK

在项目根目录执行：

```bash
./gradlew assembleDebug
```

构建产物默认在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 3. 安装到设备

如果电脑已经安装 `adb`，可以直接执行：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions 自动发布

仓库已经包含 GitHub Actions 工作流：

`/.github/workflows/release-apk.yml`

触发方式：

- 推送任意 tag

工作流会执行这些动作：

- 配置 JDK 17
- 安装 Android SDK 所需组件
- 构建可安装的 `release` APK
- 创建或复用同名 GitHub Release
- 将 APK 上传到 Release Assets

推荐的发版方式：

```bash
git tag v1.0.0
git push origin v1.0.0
```

发布后的资源文件名格式：

```text
touch-diagnostic-v1.0.0.apk
```

说明：

- 当前 `release` APK 使用调试证书签名，目的是让 GitHub Actions 在没有私有 keystore 的情况下也能产出可直接安装的 APK
- 如果后续需要正式签名，可以再改成从 GitHub Secrets 读取 keystore

## 使用说明

### 单指测试

1. 从屏幕顶部到底部，慢慢画多条横线
2. 从屏幕左侧到右侧，慢慢画多条竖线
3. 画几条斜线，观察是否存在明显断裂或漂移
4. 重点测试四边和四角

### 多指测试

1. 两根手指同时按下并分别移动
2. 三根手指同时按下并移动
3. 观察不同颜色轨迹是否持续稳定

### 网格测试

- 保持网格开启，尽量把整块屏幕都覆盖到
- 如果某些网格区域始终无法被画到，需要重点怀疑该区域触控异常

## 页面上的信息说明

- `Pointers`：当前正在触摸的手指数
- `Strokes`：已经开始过的笔画总数
- `Events`：收到的触摸事件数
- `Coverage`：已覆盖网格数 / 总网格数
- `x / y`：最后一次触摸坐标
- `Max jump`：单次轨迹跳变的最大距离，值越大越需要结合实际轨迹判断是否异常

## 按钮说明

- `Clear`：清空当前轨迹和统计
- `Hide Grid / Show Grid`：隐藏或显示网格

## 推荐测试流程

1. 先用这个 App 测单指和多指触控
2. 如果这里已经出现断触、跳点、死区，优先怀疑屏幕或驱动
3. 如果这里一切正常，再去测试业务 App 或网页
4. 如果业务页面卡，但这个 App 很顺，优先怀疑网页 / WebView / 页面渲染性能

## 已实现的核心代码

- 页面入口：[app/src/main/java/com/company/touchdiagnostic/MainActivity.kt](./app/src/main/java/com/company/touchdiagnostic/MainActivity.kt)
- 触控绘制与统计逻辑：[app/src/main/java/com/company/touchdiagnostic/TouchTestView.kt](./app/src/main/java/com/company/touchdiagnostic/TouchTestView.kt)

## 后续可扩展

- 导出测试结果截图
- 增加“异常区域标记”
- 增加触控采样频率、按压时长等统计
- 增加简易结论页，方便直接给领导看
