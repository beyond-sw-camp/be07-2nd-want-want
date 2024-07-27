package com.example.want.api.block.dto;

import com.example.want.api.block.domain.Block;
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
    private String isActivated;

    public BlockActiveListRsDto(Block block) {
        this.blockId = block.getId();
        this.title = block.getTitle();
        this.content = block.getContent();
        this.placeName = block.getPlaceName();
        this.latitude = block.getLatitude();
        this.longitude = block.getLongitude();
        this.startTime = block.getStartTime().toString();
        this.endTime = block.getEndTime().toString();
        this.isActivated = block.getIsActivated();
    }


    public static BlockActiveListRsDto fromEntity(Block block) {
        return BlockActiveListRsDto.builder()
                .blockId(block.getId())
                .title(block.getTitle())
                .content(block.getContent())
                .placeName(block.getPlaceName())
                .latitude(block.getLatitude())
                .longitude(block.getLongitude())
                .startTime(block.getStartTime().toString())
                .endTime(block.getEndTime().toString())
                .isActivated(block.getIsActivated())
                .build();
    }
}
