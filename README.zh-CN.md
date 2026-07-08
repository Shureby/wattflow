# ⚡ WattFlow

[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)

**实时显示电池真实的充放电瓦数 — 有线无线都支持。**

[English](README.md)

<p align="center">
  <img src="docs/screenshot.png" width="300" alt="应用截图" />
</p>

## 功能

- **实时功率** — 每秒采样电压 × 电流, 以瓦特显示
- **有线 & 无线** — 自动识别 AC / USB / 无线 / 底座充电
- **动画效果** — 有线时电流点沿电缆流动, 无线时波纹脉冲, 放电时流出动画
- **峰值输入 / 峰值输出** — 记录本次会话最大充电和放电功率
- **实时曲线** — 最近 60 秒功率历史, 放电显示在零线以下
- **电池信息** — 电压、电流、温度, 电量百分比嵌入电池图标
- **12 种语言** — 默认跟随系统, 可手动切换
- **零权限** — 无网络、无广告、无跟踪, 数据不出设备

## 工作原理

功率由 Android 公开 `BatteryManager` API 计算:

```
watts = EXTRA_VOLTAGE (mV) × BATTERY_PROPERTY_CURRENT_NOW (µA)
```

已处理两个常见 OEM 兼容问题: mA/µA 单位混用 (启发式判断) 与电流符号方向不统一 (归一化为充电为正)。

### 精度说明

显示的是**电池侧功率** — 真正进出电芯的功率。墙插功率总是更高:

- 转换损耗 (有线 ~10–15%, 无线 ~30–40%)
- 充电时设备本身的耗电 (屏幕、CPU、通信)

Android 没有公开适配器侧功率 API, 这已是免 root 设备能给出的最诚实数字。

### 反向充电

手机给其他设备供电时 (无线反充、OTG), Android 不提供"反向充电"标志 — 本应用将其显示为电池放电, 瓦数准确。

## 构建

需要: JDK 17+, Android SDK 34。

```bash
./gradlew assembleDebug
```

最低 Android 版本: 8.0 (API 26)。

## 许可证

[MIT](LICENSE) — 自由使用、修改、分发。
