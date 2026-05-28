package com.lingoarena.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JoinRoomRequest {
    @NotBlank
    @Size(min = 6, max = 6)
    private String roomCode;
}
