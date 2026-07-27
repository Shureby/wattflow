# Reports — Charging Q&A (draft, not yet implemented)

Status: content draft only. Not wired into the app (no Compose UI, no
strings.xml entries yet). Proposed placement: new standalone entry in the
Reports tab, alongside Ledger/Sleep/Health/Charging Benchmark — Free tier
(educational content builds trust, same rationale as the 80/20 alert
threshold ⓘ).

Written in Chinese (source language for this draft); translate into all 12
locales at implementation time, same as every other user-facing string.

Deliberately excludes any mention of the dual-cell (2S) investigation or
the removed ×2 correction — that was always an internal engineering
hypothesis, disproven and removed in v1.9.0, never something an end user
needs to be told about or reassured against.

Item 11 (wireless charging) filled in 2026-07-27 with real numbers from a
full 7%→100% wireless meter test on a 50W-wireless-capable test device
(log `dualcell-data/17t-wireless.log` — filename kept for internal
traceability only; never surface real device names in shipped copy or any
publicly-shared chart/doc, see anonymization note below).
Item 12 also updated with the system-load nuance that test surfaced.

Illustrative chart (watts vs battery %, all four datasets overlaid) built
from the real logs and published as a Claude Artifact for review:
https://claude.ai/code/artifact/6d7f6872-b3e2-4a27-8794-0488ba66b2a8 —
not yet embedded in the app; final in-app form (static bundled chart vs.
live Compose Canvas reusing the existing Session Detail curve code) still
to be decided.

**Anonymization policy, set 2026-07-27 — user's explicit call, liability
concern ("don't want to be sued"):** never name real phone models/brands
in any shipped copy, published chart, or publicly-shared doc. Test devices
are always referred to generically (device A/B/C, or by claimed spec
class only, e.g. "a phone rated for 120W wired"). This applies to chart
legends, tooltips, axis labels, and Q&A prose alike — anywhere this
content could end up in front of a user or the public. Internal-only
files (this doc's own status notes, `dualcell-data/*.log` filenames, this
roadmap memory) can keep real device references for traceability, since
those never ship or get shared.

**Test device specs (internal reference only, per the policy above — do
not carry these labels into shipped/shared content):**
- Device A: 100W max wired / 50W max wireless
- Device B: 120W max wired only
- Device C: 120W max wired only
- Charger used for all wired tests: an original 120W-max charger + its
  matching original cable
- Wireless charger: an original 55W-max wireless pad, itself powered by
  the same 120W charger + original cable

---

## 1. 为什么充电器标称功率和App显示的瓦数不一样?

充电器标称的是它"能给出"的最大功率,App显示的是手机电池"实际收到"的功率——中间隔着线材损耗和手机内部的电压转换(充电器电压通常比电池工作电压高很多,需要转换,转换过程本身会损耗一部分电)。这个差距一般在10-20%左右,属于正常范围。

## 2. 为什么快充只在电量低的时候跑最快,电量越高越慢?

锂电池充电分两阶段:前段"恒流"阶段电流拉满,功率最高;电量接近满时进入"恒压"阶段,为了保护电池电流会主动收窄,功率随之下降。所以"标称XXW"通常只在电量较低的一小段区间里才摸得到,不是全程都这么快。

## 3. 为什么换一根线,同一个充电器充电速度差很多?

不同数据线内部导线粗细、是否带E-marker芯片都不一样,便宜线材可能连基本的快充协议握手都完成不了,直接把速度限死在很低的挡位。同一充电器换根线,评级/瓦数完全可能大不相同——这也是Charging Benchmark同时记录充电器和数据线的原因。

## 4. 充电器功率标得越高,是不是充电就一定越快?

不一定。手机自己会通过协议握手,决定实际"要"多少功率,充电器标的只是它"能给"的上限——就像水龙头开到最大,水管细的话流量还是上不去。我们测的两台120W标称手机,实测峰值都只有40-57W,换更高功率的充电器不会让手机突破自己的上限。买更贵更高瓦数的充电器,不代表手机会充得更快。

## 5. 为什么换成"通用"或"第三方"充电器,速度不是慢一点点,是直接掉一大截?

很多手机用的是厂商自家的快充协议(比如小米HyperCharge、OPPO/一加的VOOC系列),只有配对的原厂充电器才能跑满速。换成通用PD/QC充电器时,手机和充电器谈不拢专有协议,会直接"降级"回一个更基础、速度低不少的通用挡位,不是线性慢一点——这解释了为什么有时候换个充电器速度像是"断崖式"下降,而不是缓慢下降。我们测试中确实见过一个充电器只谈成了~27W的PD挡位,远低于其他两个充电器测出的~40W+。

## 6. 原装充电器和第三方充电器,实测差别真的大吗?

不一定。我们在一台120W标称手机上实测过3个充电器(含2个原装),结果全部卡在同一个瓦数上限——说明这台手机当时的瓶颈在手机本身(比如电池状态),不是充电器不给力。换句话说,充电速度上不去时,先别急着怪充电器。

## 7. 为什么"标称120W"快充手机,实测峰值远低于120W?

我们实测过两台标称120W的手机,即使用原装充电器,实测峰值也只在40-57W左右,而且只在电量较低的一小段区间短暂出现,大部分时间远低于这个数字。标称功率是"理论上限",不是"全程速度",这个差距在快充手机上很常见。

## 8. 电池用久了,为什么同一个充电器充得越来越慢?

电池老化后内阻会变大,手机的电池管理芯片(BMS)会主动降低允许通过的最大电流,以避免老化电池在大电流下过热或加速损坏。这是电池自我保护机制,不是App或充电器的问题——可以在Reports里看Battery Health Trend,如果满电容量明显低于电池标称容量,基本能解释充电变慢。

## 9. 为什么手机充到100%,App还显示有几瓦在充电?

到达100%后,电池管理芯片通常还会做一小段"涓流补充/维持充电",防止电量因自然漏电慢慢掉下去,这段电流很小而且会继续衰减到零。不同手机这段行为差别很大——我们实测过的手机里,有的一到100%电流立刻归零,有的会再持续一两分钟慢慢衰减,都属于正常现象。

## 10. 为什么整夜插着充电,速度看起来忽快忽慢,甚至半夜很久不涨?

不少手机(尤其新机型)有"自适应/智能充电"功能:检测到你在整夜充电时,会先充到七八成就放慢或暂停,推算你大概起床的时间,再抓紧在那之前冲满100%——目的是减少电池长时间处于满电状态的时间,延长电池寿命。半夜看到进度卡住不涨,大概率是这个功能在起作用,不是App读数出错或手机故障。

## 11. 无线充电为什么损耗比有线充电大很多?

USB功率计测的是"流进无线充电板"的电量,不是充电板对手机的实际"输出"——磁场没法直接接功率计。所以对比的其实是【充电板输入功率】vs【手机电池实际接收功率】,这跨越了一整条链路:直流转交流→线圈耦合→手机侧整流→充电管理芯片,比有线场景多了好几级转换。有线场景功率计接在充电器和手机之间,离电池很"近",只隔一层线材+手机内部转换,所以差距一直很小(~10-20%)。

无线场景的差距通常明显更大(30-45%甚至更多),而且会随线圈对齐精度、功率大小、散热状况明显波动——这些损耗大多变成热量,散在充电板和手机背壳上。

**实测数据(一台标称50W无线充电的手机,7%→100%完整一轮):** 屏幕亮着时差距约37-42%(读数比约0.58-0.63),屏幕关闭后收窄到约28-34%(读数比约0.66-0.72)。这约6个百分点的差别,更可能是手机屏幕本身耗电(背光通常1-3W)从同一路输入功率里先分走一部分,而不是无线耦合效率本身随屏幕状态变化——App只读电池侧电流,不读整机总耗电,所以屏幕亮着时,一部分充电板送来的电还没到电池就被屏幕用掉了,差距自然显得更大。整条链路(直流转交流→线圈耦合→整流→充电管理)的实际效率大约在58-72%区间(即28-42%损耗),具体数字随屏幕状态、对齐、功率浮动。

## 12. 为什么用外部USB功率计量,读数总是比App显示的瓦数高一点?

功率计测的是充电器/充电线这一端的电,App显示的是电池那一端实际收到的电,中间隔着线材电阻和手机内部电压转换,损耗是物理上必然存在的。我们在多台手机、多种功率下反复测过,这个差距稳定维持在10-20%,不会随功率升高而失控放大——这是判断"读数是否可信"的一个简单标准:差距应该是个小比例,不该是成倍的。

这个差距其实是两部分加在一起:线材/转换损耗,加上手机屏幕、处理器等自身耗电——App只读电池那一端的电流,不读整机总耗电,所以屏幕亮着、后台跑着任务时,一部分电还没进电池就被手机自己用掉了,会让差距看起来比纯转换损耗更大一些(无线充电这一点更明显,见第11条)。

## 13. WattFlow的瓦数是怎么算出来的?精度极限在哪?

直接读取Android系统提供的电池电压和电流(手机内部的电量计硬件测出来的),两者相乘就是瓦数。这意味着精度上限取决于手机自己的电量计硬件有多准——不同手机、不同厂商的传感器精度本身就有差异,App不会也不能去"修正"硬件本身的系统性误差,只忠实呈现读到的数字。

## 14. 为什么给别的设备供电(反向充电/OTG)时,App里显示的是"放电"?

Android系统本身没有专门的"反向充电"状态标志,只区分"充电"和"放电"。所以手机给耳机、给另一部手机供电时,系统只能把它归类为放电,App如实显示——瓦数本身是准确的,只是方向标签用的是系统仅有的两个选项之一。
