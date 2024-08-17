package com.example.want.api.member.dto;

import com.example.want.api.project.dto.ProjectDetailRsDto;
import com.example.want.api.state.domain.ProjectState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InvitationResDto {
    private Long projectId;
    private String projectTitle;
    private String invitationAccepted;

    private LocalDateTime createdTime;
    private String startTravel;
    private String endTravel;
    private String inviterName;
    // todo 여행 장소 추가
    private List<ProjectDetailRsDto.ProjectStateList> projectStates;
}
