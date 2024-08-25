package com.example.want.api.block.dto;

import com.example.want.api.block.domain.Block;
import com.example.want.api.block.domain.Category;
import com.example.want.api.project.domain.Project;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportBlockRqDto {
    private Long projectId;
    private Long blockId;

    public Block toImport(Block findBlock, Project project) {
        return Block.builder()
                .title(findBlock.getTitle())
                .content(findBlock.getContent())
                .category(findBlock.getCategory())
                .project(project)
                .heartCount(0L)
                .isActivated("N")
                .latitude(findBlock.getLatitude())
                .longitude(findBlock.getLongitude())
                .build();
    }
}