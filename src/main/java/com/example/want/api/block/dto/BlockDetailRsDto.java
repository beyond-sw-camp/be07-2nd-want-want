package com.example.want.api.block.dto;

import com.example.want.api.block.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockDetailRsDto {
    private Long blockId;
    private String creator;
    private String title;
    private String content;
    private String placeName;
    private Category category;
    private Double latitude;
    private Double longitude;
    private String startTime;
    private String endTime;
    private String isActivated;
    private Long heartCount;
    private Boolean isHearted;

}
