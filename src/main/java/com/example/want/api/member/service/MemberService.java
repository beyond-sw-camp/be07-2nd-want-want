package com.example.want.api.member.service;

import com.example.want.api.member.domain.Member;
import com.example.want.api.member.dto.AcceptInvitationDto;
import com.example.want.api.member.dto.InvitationResDto;
import com.example.want.api.member.repository.MemberRepository;
import com.example.want.api.project.domain.Project;
import com.example.want.api.project.dto.ProjectDetailRsDto;
import com.example.want.api.project.repository.ProjectRepository;
import com.example.want.api.projectMember.Repository.ProjectMemberRepository;
import com.example.want.api.projectMember.domain.ProjectMember;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;

    public Member getMyProfile(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("해당 사용자를 찾을 수 없습니다."));

    }

    // TODO: 7/29/24 is done 이런거 확인
//    초대 요청 목록 조회
    public Page<InvitationResDto> getMyInvitations(String email, Pageable pageable) {
//        사용자의 email을 이용하여 projectMember에서 List를 가져옴
        Page<ProjectMember> projectMembers = projectMemberRepository.findByMemberEmailAndInvitationAccepted(email, pageable,"N");
        List<InvitationResDto> invitationResDtos = new ArrayList<>();

//        Page 객체는 페이징 정보를 포함하고 있으므로, 실제 순수 데이터를 리스트로 가져오려면
//        getContent() 메서드를 사용해야함
        for (ProjectMember projectMember : projectMembers.getContent()) {
            Project project = projectRepository.findById(projectMember.getProject().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Project Not found"));

            String inviterName = projectMember.getInviterName();

            InvitationResDto invitationResDto = InvitationResDto.builder()
                    .projectId(project.getId())
                    .projectTitle(project.getTitle())
                    .invitationAccepted(projectMember.getInvitationAccepted())
                    .createdTime(projectMember.getCreatedTime())
                    .startTravel(project.getStartTravel().toString())
                    .endTravel(project.getEndTravel().toString())
                    // todo 여행 장소 추가
                    .projectStates(project.getProjectStates().stream()
                            .map(projectState -> ProjectDetailRsDto.ProjectStateList.builder()
                                    .stateId(projectState.getState().getId())
                                    .country(projectState.getState().getCountry())
                                    .city(projectState.getState().getCity())
                                    .build())
                            .collect(Collectors.toList()))
                    .inviterName(inviterName)
                    .build();
            invitationResDtos.add(invitationResDto);
        }
//        PageImpl(List<T> content, Pageable pageable, long total)
        return new PageImpl<>(invitationResDtos, pageable, projectMembers.getTotalElements());
    }

//    초대 요청 수락
    @Transactional
    public void invitationAcceptOrReject(String email, AcceptInvitationDto dto) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Member Not found"));
        ProjectMember projectMember = projectMemberRepository.findByMemberAndProjectId(member, dto.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("ProjectMember Not found"));

////        초대 코드 검증
//        if (!projectMember.getInvitationCode().equals(dto.getInvitationCode())) {
//            throw new IllegalArgumentException("Uncorrected invitation code");
//        }

        if (dto.getAction().equals("accept")) {
            if (projectMember.getInvitationAccepted().equals("Y")) {
                throw new IllegalArgumentException("Invitation already accepted");
            }
            projectMember.setInvitationAccepted("Y");
            projectMember.setIsExist("Y");  // 초대 수락시 isExist = 'Y'로 변경
            projectMemberRepository.save(projectMember);
        } else if ("reject".equals(dto.getAction())) {
            projectMemberRepository.delete(projectMember);
        } else {
            throw new IllegalArgumentException("Invalid action");
        }
    }
}
