package com.lingoarena.enums;

/**
 * 房间状态枚举，对应游戏生命周期。
 * WAITING  - 等待中：房间已创建，等待第二位玩家加入并开始游戏
 * PLAYING  - 游戏中：双方正在答题
 * FINISHED - 已结束：游戏完成，已出结果
 * CANCELLED - 已取消：房间被关闭，未正常完成游戏
 */
public enum RoomStatus {
    WAITING, PLAYING, FINISHED, CANCELLED
}
