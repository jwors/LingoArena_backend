package com.lingoarena.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class StatsResponse {
    private long totalGames;
    private long wins;
    private long losses;
    private double avgScore;
}
