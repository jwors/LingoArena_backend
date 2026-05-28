package com.lingoarena.dto.request;

import com.lingoarena.enums.GameMode;
import lombok.Data;

@Data
public class CreateRoomRequest {
    private Long wordbookId;
    private Integer totalRounds;
    private GameMode gameMode;
}
