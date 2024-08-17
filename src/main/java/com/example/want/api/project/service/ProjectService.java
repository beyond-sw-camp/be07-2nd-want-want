package com.example.want.api.project.service;

import com.example.want.api.member.domain.Member;
import com.example.want.api.member.repository.MemberRepository;
import com.example.want.api.project.domain.Authority;
import com.example.want.api.project.domain.Project;
import com.example.want.api.project.dto.*;
import com.example.want.api.project.repository.ProjectRepository;
import com.example.want.api.projectMember.Repository.ProjectMemberRepository;
import com.example.want.api.projectMember.domain.ProjectMember;
import com.example.want.api.state.domain.ProjectState;
import com.example.want.api.state.domain.State;
import com.example.want.api.state.repository.StateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final MemberRepository memberRepository;
    private final StateRepository stateRepository;


    //    일정 생성, 팀원 목록 생성 (일정 생성자가 리더가 됨)
    @Transactional
    public ProjectCreateResDto createProject(ProjectCreateReqDto dto, String email) {
        Member member = findMemberByEmail(email);
//        일정 생성
        Project project = dto.toEntity();
        project = projectRepository.save(project);
//        리더 생성
        ProjectMember projectMember = createProjectLeader(member, project);
        projectMember.setIsExist("Y");
//        지역 생성
        State state = stateRepository.findByCountryAndCity(dto.getState().getCountry(), dto.getState().getCity())
                        .orElseGet(() -> stateRepository.save(State.builder()
                                .country(dto.getState().getCountry())
                                .city(dto.getState().getCity())
                                .build()));
        ProjectState projectState = ProjectState.builder()
                .state(state)
                .project(project)
                .build();
        project.getProjectStates().add(projectState);

        project.getProjectMembers().add(projectMember);
        project.initializeFields();
        return ProjectCreateResDto.fromEntity(project, state);
    }
    private ProjectMember createProjectLeader(Member member, Project project) {
        return ProjectMember.builder()
                .member(member)
                .authority(Authority.LEADER)
                .invitationAccepted("Y")
                .project(project)
                .build();
    }


    // 일정 수정
    @Transactional
    public ProjectDatesUpdateRsDto updateTravelDates(Long projectId, ProjectDatesUpdateRqDto projectDatesUpdateRqDto, String email) {
        Member member = findMemberByEmail(email);
        Project project = findProjectById(projectId);
        if (project.getProjectMembers().stream().noneMatch(projectMember -> projectMember.getMember().equals(member))) {
            throw new IllegalArgumentException("프로젝트에 접근할수있는 유저가 아닙니다.");
        }
        project.updateTravelDates(projectDatesUpdateRqDto.getStartTravel(), projectDatesUpdateRqDto.getEndTravel());

        return ProjectDatesUpdateRsDto.builder()
                .projectId(project.getId())
                .startTravel(project.getStartTravel())
                .endTravel(project.getEndTravel())
                .build();
    }

    @Transactional
    // 제목 수정
    public ProjectTitleUpdateRsDto updateTitle(Long projectId, String newTitle, String email) {
        Member member = findMemberByEmail(email);
        Project project = findProjectById(projectId);
        if (project.getProjectMembers().stream().noneMatch(projectMember -> projectMember.getMember().equals(member))) {
            throw new IllegalArgumentException("프로젝트에 접근할수있는 유저가 아닙니다.");
        }
        project.updateTitle(newTitle);

        return ProjectTitleUpdateRsDto.builder()
                .id(project.getId())
                .title(project.getTitle())
                .build();

    }

    // 일정 삭제
    @Transactional
    public void deleteProject(Long projectId, String email) {
        Project project = findProjectById(projectId);
        Member member = findMemberByEmail(email);

        ProjectMember projectMember = projectMemberRepository.findByProjectAndMember(project, member)
                .orElseThrow(() -> new EntityNotFoundException("TravelUser not found"));

        if (projectMember.getAuthority() == Authority.LEADER) {
            project.delete();

            List<ProjectMember> projectMembers = projectMemberRepository.findByProject(project);
            for (ProjectMember pm : projectMembers) {
                pm.updateIsExist("N");
            }
            projectRepository.save(project);

        } else {
            projectMember.updateIsExist("N");
            projectMemberRepository.save(projectMember);
        }
    }

//    팀원 초대
    @Transactional
    public void inviteUser (Long projectId, String otherMemberEmail, String email) {
//        초대할 member 객체
        Member member = findMemberByEmail(email);
        Member otherMember = memberRepository.findByEmail(otherMemberEmail)
                .orElseThrow(() -> new EntityNotFoundException("초대하려는 멤버 이메일이 없습니다."));
        Project project = findProjectById(projectId);
        if (project.getProjectMembers().stream().noneMatch(projectMember -> projectMember.getMember().equals(member))) {
            throw new IllegalArgumentException("프로젝트에 접근할수있는 유저가 아닙니다.");
        }

        System.out.println("tqqqqqqqqqqqqqqqqqqqqqq");
//        멤버가 이미 팀원목록에 속해 있는지 확인하는 검증코드
        boolean existsMember = projectMemberRepository.existsByProjectAndMember(project, otherMember);
        if(existsMember) {
            throw new IllegalArgumentException("Member already exists.");
        }

        ProjectMember projectMember = ProjectMember.builder()
                .project(project)
                .member(otherMember)
                .authority(Authority.MEMBER)
                .invitationAccepted("N")
                .inviterName(member.getName())// 초대 수락을 하면 "Y"로 변경
                .build();

        projectMemberRepository.save(projectMember);
    }

    public Page<MyProjectListRsDto> getMyProjectList(Pageable pageable, String email) {
        Member member = findMemberByEmail(email);
        Page<MyProjectListRsDto> myProjectListRsDto = projectMemberRepository.findActiveProjectByMember(member, pageable)
                .map(project -> MyProjectListRsDto.builder()
                        .projectId(project.getId())
                        .projectTitle(project.getTitle())
                        .startTravel(project.getStartTravel().toString())
                        .endTravel(project.getEndTravel().toString())
                        .createdTime(project.getCreatedTime().toString())
                        .isDone(project.getIsDone())
                        .travelUsers(project.getProjectMembers().stream()
                                .map(projectMember -> MyProjectListRsDto.MyProjectMember.builder()
                                        .userId(projectMember.getMember().getId())
                                        .userName(projectMember.getMember().getName())
                                        .userProfile(projectMember.getMember().getProfileUrl())
                                        .build())
                                .collect(Collectors.toList()))
                        .build());
        return myProjectListRsDto;
    }

    public ProjectDetailRsDto getProjectDetail(Long projectId, String email) {

        if (projectId == null) {
            System.out.println("projectId가 null입니다.");
        }
        if (email == null) {
            System.out.println("email이 null입니다.");
        }

        Member member = findMemberByEmail(email);
        if (member == null) {
            System.out.println("member가 null입니다.");
        }

        Project project = findProjectById(projectId);
        if (project == null) {
            System.out.println("project가 null입니다.");
        }

        if (project.getProjectMembers().stream().noneMatch(projectMember -> projectMember.getMember().equals(member))) {
            throw new IllegalArgumentException("프로젝트에 접근할수있는 유저가 아닙니다.");
        }

        return ProjectDetailRsDto.builder()
                .projectId(project.getId())
                .projectTitle(project.getTitle())
                .startTravel(project.getStartTravel())
                .endTravel(project.getEndTravel())
                .projectMembers(project.getProjectMembers().stream()
                        .map(projectMember -> ProjectDetailRsDto.ProjectMemberList.builder()
                                .userId(projectMember.getMember().getId())
                                .userName(projectMember.getMember().getName())
                                .userProfile(projectMember.getMember().getProfileUrl())
                                .build())
                        .collect(Collectors.toList()))
                .projectStates(project.getProjectStates().stream()
                        .map(projectState -> ProjectDetailRsDto.ProjectStateList.builder()
                                .stateId(projectState.getState().getId())
                                .country(projectState.getState().getCountry())
                                .city(projectState.getState().getCity())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }



    private Member findMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("Member not found"));
    }
    private Project findProjectById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
    }

    public boolean isMemberOfProject(Long projectId, String email) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid project ID"));

        // projectMembers에서 이메일이 일치하고 isExists가 'Y'인 경우에만 true를 반환
        return project.getProjectMembers().stream()
                .anyMatch(member -> member.getMember().getEmail().equals(email) && "Y".equals(member.getIsExist()));
    }

}
