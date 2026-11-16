---
name: citywalk-map
description: 把已经规划好的城市漫步路线放进高德地图显示（起点、途经景点、终点，公共交通方案）
apps: [com.autonavi.minimap]
channel: deeplink-then-gui
deeplinks:
  route: "amapuri://route/plan/?sourceApplication=xiaoman&sid=&slat={slat}&slon={slon}&sname={sname}&did=&dlat={dlat}&dlon={dlon}&dname={dname}&dev=0&t=1&m=0&vian={vian}&vialons={vialons}&vialats={vialats}&vianames={vianames}"
---
# 在高德地图显示漫步路线

目标：把一条已经规划好的 City Walk 路线放进高德地图的「公共交通」路线页，让用户一眼看到整条路径。

0. 出行方式固定为公共交通（deeplink 里 `t=1`，不能改成步行 `t=2` 或驾车 `t=0`），
   打开后顶部「公共交通」是选中态；用户要地铁优先，落页后可在「偏好」里切换。
1. 用 deeplink `route` 打开路线页：起点 `sname`/`slat`/`slon`，终点 `dname`/`dlat`/`dlon`。
2. 中间景点按顺序填 `vialons`/`vialats`/`vianames`，多个值之间只能用竖线 `|`（不能用逗号）；
   `vian` 是途经点个数，必须与 vialons 的数量一致；没有途经点时 `vian=0`，三个 via 参数留空。
3. 经纬度一律用目标里给的坐标，不要自己编。
4. 路线页出现「终点信息来自其他软件」是正常的，不用点「去确认」。
5. 不要点「开始导航」，不要修改终点或增删途经点；路线页打开后直接 finish，
   summary 写「已在高德按公共交通显示 {起点} → {终点} 的路线」。

注意：amapuri 打不开时 finish 并说明原因，不要反复重试。
