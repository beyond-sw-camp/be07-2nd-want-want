package com.example.want.api.block.dto;

import com.example.want.api.block.domain.Block;
import com.example.want.api.block.domain.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlockActiveListRsDto {
    private Long blockId;
    private String title;
    private String content;
    private String placeName;
    private Double latitude;
    private Double longitude;
    private String startTime;
    private String endTime;
    private Long heartCount;
    private Long popularCount;
    private Category category;
    private String isActivated;
    private Boolean isHearted;


    public static BlockActiveListRsDto fromEntity(Block block) {
        return BlockActiveListRsDto.builder()
                .blockId(block.getId())
                .category(block.getCategory())
                .title(block.getTitle())
                .content(block.getContent())
                .placeName(block.getPlaceName())
                .latitude(block.getLatitude())
                .longitude(block.getLongitude())
                .startTime(block.getStartTime() != null ? block.getStartTime().toString() : null)
                .endTime(block.getEndTime() != null ? block.getEndTime().toString() : null)
                .heartCount(block.getHeartCount())
                .popularCount(block.getPopularCount())
                .isActivated(block.getIsActivated())
                .build();
    }
}
