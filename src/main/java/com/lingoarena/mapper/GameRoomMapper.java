package com.lingoarena.mapper;

import com.lingoarena.dto.response.RoomResponse;
import com.lingoarena.entity.GameRoom;
import com.lingoarena.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * GameRoom 实体和 DTO 之间的映射。
 * 这里涉及多个关联对象（host、guest、wordbook）的嵌套映射。
 */
@Mapper(componentModel = "spring")
public interface GameRoomMapper {

    /** 将 GameRoom 实体转换为 RoomResponse DTO */
    @Mapping(target = "host", source = "gameRoom.host", qualifiedByName = "toPlayerInfo")
    @Mapping(target = "guest", source = "gameRoom.guest", qualifiedByName = "toPlayerInfo")
    @Mapping(target = "wordbookId", source = "gameRoom.wordbook.id")
    @Mapping(target = "wordbookName", source = "gameRoom.wordbook.name")
    @Mapping(target = "winnerId", source = "gameRoom.winner.id")
    RoomResponse toResponse(GameRoom gameRoom);

    /** User → RoomResponse.UserInfo（只暴露 id 和昵称，不暴露密码等敏感信息） */
    @Named("toPlayerInfo")
    default RoomResponse.UserInfo toPlayerInfo(User user) {
        if (user == null) return null;
        return RoomResponse.UserInfo.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .build();
    }
}
