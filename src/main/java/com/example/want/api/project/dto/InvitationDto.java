package com.example.want.api.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class InvitationDto {
    private Long projectId;
    private String email;  // 초대 받는 사람의 이메일
    private String inviterName;  // 초대 보내는 사람의 이름
}
