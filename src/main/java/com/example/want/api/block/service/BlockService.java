package com.example.want.api.block.service;

import com.example.want.api.block.domain.Block;
import com.example.want.api.block.domain.Category;
import com.example.want.api.block.dto.*;
import com.example.want.api.block.repository.BlockRepository;
import com.example.want.api.heart.domain.Heart;
import com.example.want.api.heart.repository.HeartRepository;
import com.example.want.api.member.domain.Member;
import com.example.want.api.member.login.UserInfo;
import com.example.want.api.member.repository.MemberRepository;
import com.example.want.api.project.domain.Project;
import com.example.want.api.project.repository.ProjectRepository;
import com.example.want.api.projectMember.Repository.ProjectMemberRepository;
import com.example.want.api.state.domain.State;
import com.example.want.api.state.repository.StateRepository;
import com.example.want.api.sse.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BlockService {
    private final BlockRepository blockRepository;
    private final MemberRepository memberRepository;
    private final HeartRepository heartRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final StateRepository stateRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final NotificationService notificationService;

    @Qualifier("heart")
    private final RedisTemplate<String, Object> heartRedisTemplate;


    @Transactional
    public Block createBlock(CreateBlockRqDto request, UserInfo userInfo) {
        Project project = validateProjectMember(request.getProjectId(), userInfo.getEmail());
        Block block = request.toEntity(request.getCategory(), project);
        return blockRepository.save(block);
    }
    private Project validateProjectMember(Long projectId, String memberEmail) {
        Member member = memberRepository.findByEmail(memberEmail)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 없습니다."));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트가 없습니다."));
        if (!projectMemberRepository.existsByProjectAndMember(project, member)) {
            throw new IllegalArgumentException("해당 프로젝트의 멤버가 아닙니다.");
        }
        return project;
    }

    public List<BlockActiveListRsDto> getNotActiveBlockList(Long projectId, String memberEmail, Category category) {
        // 프로젝트와 회원을 검증합니다.
        Project project = validateProjectMember(projectId, memberEmail);
        Member member = getMemberByEmail(memberEmail);

        List<Block> blocks;
        // Category가 null인 경우와 아닌 경우에 따라 쿼리를 다르게 수행
        if (category == null) {
            blocks = blockRepository.findAllByProjectAndIsActivatedAndIsDeleted(project, "N", "N"); // Boolean 타입으로 변경
        } else {
            blocks = blockRepository.findAllByProjectAndIsActivatedAndCategoryAndIsDeleted(project, "N", category, "N"); // Boolean 타입으로 변경
        }
        // Block 리스트에서 Hearted 상태를 체크하여 DTO로 변환
        return blocks.stream()
                .map(block -> {
                    BlockActiveListRsDto dto = BlockActiveListRsDto.fromEntity(block);
                    // Heart 상태를 확인하고 DTO에 반영
                    dto.setIsHearted(heartRepository.existsByMemberAndBlock(member, block));
                    return dto;
                })
                .collect(Collectors.toList());
    }


    public Page<BlockActiveListRsDto> getActiveBlockList(Long projectId, String memberEmail , Pageable pageable) {
        Project project = validateProjectMember(projectId, memberEmail);
        Page<Block> blockList = blockRepository.findAllByProjectAndIsActivatedOrderByStartTimeAsc(project ,"Y", pageable);
        return blockList.map(b -> BlockActiveListRsDto.fromEntity(b));
    }

    public BlockDetailRsDto getBlockDetail(Long id, UserInfo userInfo) {
        Block block = blockRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다."));
        Member member = getMemberByEmail(userInfo.getEmail());
        BlockDetailRsDto detailDto = block.toDetailDto();
        detailDto.setIsHearted(heartRepository.existsByMemberAndBlock(member, block));
        return detailDto;
    }
    @Transactional
    public Block addLikeToPost(Long blockId, String memberEmail) {
        Block block = getBlockById(blockId);
        Member member = getMemberByEmail(memberEmail);

        boolean alreadyLiked = heartRepository.existsByMemberAndBlock(member, block);

        if (alreadyLiked) {
            removeLike(member, block);
        } else {
            addLike(member, block);
        }

        return updateRedisHeartCount(block);
    }

    private Block getBlockById(Long blockId) {
        return blockRepository.findById(blockId)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다."));
    }

    private Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 없습니다."));
    }

    private void removeLike(Member member, Block block) {
        Heart heart = heartRepository.findByMemberAndBlock(member, block)
                .orElseThrow(() -> new IllegalStateException("좋아요가 존재하지 않습니다."));
        heartRepository.delete(heart);
        block.decrementHearts();
        blockRepository.save(block);
    }

    private void addLike(Member member, Block block) {
        Heart heart = Heart.builder().member(member).block(block).build();
        heartRepository.save(heart);
        block.incrementHearts();
        blockRepository.save(block);
    }

    private Block updateRedisHeartCount(Block block) {
        HashOperations<String, String, Object> hashOperations = heartRedisTemplate.opsForHash();
        String key = "blockId:heart::" + block.getId();
        String hashKey = "heartCount";

        // Redis에서 좋아요 수를 업데이트
        hashOperations.put(key, hashKey, block.getHeartCount());
        return block;
    }

    public Long getLikesCount(Long blockId) {
        String key = "blockId:heart::" + blockId;
        String hashKey = "heartCount";

        Long likesCount = getLikesCountFromCache(key, hashKey);
        if (likesCount == null) {
            likesCount = getLikesCountFromDb(blockId);
            updateCache(key, hashKey, likesCount);
        }

        return likesCount;
    }

    private Long getLikesCountFromCache(String key, String hashKey) {
        HashOperations<String, String, Object> hashOperations = heartRedisTemplate.opsForHash();
        Object cachedValue = hashOperations.get(key, hashKey);

        if (cachedValue instanceof Long) {
            return (Long) cachedValue;
        } else if (cachedValue instanceof Integer) {
            return ((Integer) cachedValue).longValue();
        }

        return null;
    }

    private Long getLikesCountFromDb(Long blockId) {
        return blockRepository.findById(blockId)
                .map(Block::getHeartCount)
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 없습니다."));
    }

    private void updateCache(String key, String hashKey, Long likesCount) {
        HashOperations<String, String, Object> hashOperations = heartRedisTemplate.opsForHash();
        hashOperations.put(key, hashKey, likesCount);
    }

    // Block 을 끌어다 놓음 -> Block 에 일정 날짜 등록
    @Transactional
    public BlockDetailRsDto addDateBlock(AddDateBlockRqDto addDateRqDto, String memberEmail) {
        Block block = getBlockById(addDateRqDto.getBlockId());
        Member member = getMemberByEmail(memberEmail);
        String startTime = addDateRqDto.getStartTime();
        String endTime = addDateRqDto.getEndTime();
        System.out.println("startTime = " + startTime);
        System.out.println("endTime = " + endTime);
        // 포맷터
        OffsetDateTime offsetDateTime1 = OffsetDateTime.parse(addDateRqDto.getStartTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        OffsetDateTime offsetDateTime2 = OffsetDateTime.parse(addDateRqDto.getEndTime(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        OffsetDateTime kstStartTime = offsetDateTime1.withOffsetSameInstant(ZoneOffset.ofHours(9));
        OffsetDateTime kstEndTime = offsetDateTime2.withOffsetSameInstant(ZoneOffset.ofHours(9));
        LocalDateTime localDateTime1 = kstStartTime.toLocalDateTime();
        LocalDateTime localDateTime2 = kstEndTime.toLocalDateTime();


        block.getProject().getProjectMembers().stream()
                .filter(projectMember -> projectMember.getMember().equals(member))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트의 멤버가 아닙니다."));

        block.updatePlan(localDateTime1, localDateTime2);

        // 프로젝트 ID 가져오기
        Long projectId = block.getProject().getId();

        // Redis 채널에 알림 메시지 발행 (JSON 형식)
        String notificationMessage = "{ \"projectId\": " + projectId + ", \"message\": \"Block " + block.getId() + " has been activated by " + memberEmail + "\" }";
        stringRedisTemplate.convertAndSend("project:notifications", notificationMessage);

        return block.toDetailDto();
    }

    // 날짜별 Block 조회
    public List<BlockActiveListRsDto> getBlocksByDate(Long projectId , LocalDate date, String memberEmail ) {// "2024-07-26T09:00" 형식의 문자열
        Project project = validateProjectMember(projectId, memberEmail);
        LocalDateTime startDate = date.atStartOfDay();
        LocalDateTime endDate = date.atStartOfDay().plusDays(1);
        List<Block> blocks = blockRepository.findByProjectAndIsActivatedAndStartTimeBetweenOrderByStartTimeAsc(project,"Y", startDate, endDate);
        List<BlockActiveListRsDto> blockActiveListRsDtos = blocks.stream()
                .map(BlockActiveListRsDto::fromEntity)
                .collect(Collectors.toList());
        return blockActiveListRsDtos;
    }

    // 좋아요 수에 따라 Block 을 정렬하여 반환하는 메서드
    // 프로젝트별로 조회할 수 있도록 추가.
    @Transactional
    public Page<BlockActiveListRsDto> activeBlocksByPopular(Long projectId, Pageable pageable) {
        Page<Block> blocks = blockRepository.findByProjectIdAndIsActivatedOrderByHeartCountDesc(projectId, "Y", pageable);
        return blocks.map(BlockActiveListRsDto::fromEntity);
    }

    // 카테고리 별로 Block 조회하기
    public Page<BlockActiveListRsDto> getBlocksByCategory(Long projectId, Category category , String email , Pageable pageable) {
        Project project = validateProjectMember(projectId, email);
        Page<Block> blocks = blockRepository.findByProjectAndCategory(project,category, pageable);
        return blocks.map(BlockActiveListRsDto::fromEntity);
    }

//    public BlockDetailRsDto updateBlockTitle(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        block.updateTitle(request.getTitle());
//        return block.toDetailDto();
//    }
//
//    public BlockDetailRsDto updateBlockContent(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        block.updateContent(request.getContent());
//        return block.toDetailDto();
//    }
//
//    public BlockDetailRsDto updateBlockPlaceName(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        block.updatePlaceName(request.getPlaceName());
//        return block.toDetailDto();
//    }
//
//    public BlockDetailRsDto updateBlockCategory(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        block.updateCategory(request.getCategory());
//        return block.toDetailDto();
//    }
//
//    public BlockDetailRsDto updateBlockIsActivated(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        if (request.getIsActivated().equals("Y")) {
//            block.changeIsActivated("N");
//        } else {
//            block.changeIsActivated("Y");
//        }
//        block.changeIsActivated(request.getIsActivated());
//        return block.toDetailDto();
//    }

//    public BlockDetailRsDto updateBlockTime(Long id, UpdateBlockRqDto request, UserInfo userInfo) {
//        Member member = getMemberByEmail(userInfo.getEmail());
//        Block block = getBlockById(id);
//        LocalDateTime startTime = LocalDateTime.parse(request.getStartTime());
//        LocalDateTime endTime = LocalDateTime.parse(request.getEndTime());
//        block.updatePlan(startTime, endTime );
//        return block.toDetailDto();
//    }

    @Transactional
    public BlockDetailRsDto updateBlock(Long id, UpdateBlockRqDto updateBlockRqDto, UserInfo userInfo) {

        Block block = blockRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Block not found"));

        if (updateBlockRqDto.getTitle() != null) {
            block.updateTitle(updateBlockRqDto.getTitle());
        }
        if (updateBlockRqDto.getContent() != null) {
            block.updateContent(updateBlockRqDto.getContent());
        }
        if (updateBlockRqDto.getPlaceName() != null) {
            block.updatePlaceName(updateBlockRqDto.getPlaceName());
        }
        if (updateBlockRqDto.getLatitude() != null && updateBlockRqDto.getLongitude() != null) {
            block.updatePoint(updateBlockRqDto.getLatitude(), updateBlockRqDto.getLongitude());
        }

        if (updateBlockRqDto.getStartTime() != null && updateBlockRqDto.getEndTime() != null) {
            block.updatePlan(updateBlockRqDto.getStartTime(), updateBlockRqDto.getEndTime());
        }
        if (updateBlockRqDto.getCategory() != null) {
            block.updateCategory(updateBlockRqDto.getCategory());

        }

    return block.toDetailDto();
    }

    @Transactional
    public Block blockDelete(UserInfo userInfo, Long id) {
        Block block = blockRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));
        block.getProject().getProjectMembers().stream()
                .filter(projectMember -> projectMember.getMember().getEmail().equals(userInfo.getEmail()))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("해당 게시글이 속한 프로젝트의 팀원이 아닙니다."));
        block.changeIsDelete();
        return block;
    }

    @Transactional
    public BlockDetailRsDto notActiveBlock(Long blockId, String email) {
        Block block = blockRepository.findById(blockId)
                .orElseThrow(() -> new EntityNotFoundException("해당 게시글이 없습니다."));
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("해당 회원이 없습니다."));
        block.getProject().getProjectMembers().stream()
                .filter(projectMember -> projectMember.getMember().equals(member))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("해당 프로젝트의 멤버가 아닙니다."));

        block.changeIsActivated("N");
        return block.toDetailDto();
    }

    @Transactional
    public List<BlockActiveListRsDto> getBlocksByState(Long stateId) {
        List<Block> blocks = new ArrayList<>();
        List<BlockActiveListRsDto> blockDtos = new ArrayList<>();
        State state = stateRepository.findById(stateId)
                .orElseThrow(() -> new EntityNotFoundException("State not found"));

        List<Project> projects = projectRepository.findByProjectStatesState(state);

        for (Project project : projects) {
            blocks.addAll(blockRepository.findAllByProject(project));
        }

        Map<String, BlockActiveListRsDto> blockDtoMap = new HashMap<>();
        for (Block block : blocks) {
            // key 는 블럭의 "위도, 경도"
            String key = block.getLatitude() + ","+ block.getLongitude();

            // 지금 for 문을 도는 블럭의 위도, 경도가 dto 리스트에 이미 있는지 비교하기 위해
            // dto 리스트에서 key 값을 찾는다.
            BlockActiveListRsDto foundBlockdto = blockDtoMap.get(key);

            // 만약 key 가 비어있지 않다면, 해당 "위도,경도" 를 가지는 블럭이 이미 있다는 뜻이므로
            // 지금 for 문을 도는 블럭은 추가되지 않고, 기존에 있던 dto 리스트에 있는 블럭의 popularCount 값만 ++ 해준다.
            if(foundBlockdto != null){
                // 해당 key 값을 가지는 dto 리스트 안의 블럭의 count ++.
                foundBlockdto.incrementPopularCount();
            }
            else{
                BlockActiveListRsDto newDto = BlockActiveListRsDto.fromEntity(block);
                blockDtoMap.put(key, newDto);
            }
        }

        blockDtos.addAll(blockDtoMap.values());

        return blockDtos;
    }

    @Transactional
    public Block importBlock(UserInfo userInfo, ImportBlockRqDto importDto) {
        Project project = validateProjectMember(importDto.getProjectId(), userInfo.getEmail());
        Block findBlock = blockRepository.findById(importDto.getBlockId()).orElseThrow(() -> new EntityNotFoundException("해당 블록을 찾을 수 없습니다."));
        Block block = importDto.toImport(findBlock, project);
        System.out.println(block);
        return blockRepository.save(block);
    }


    public Long findProjectIdByBlockId(Long blockId) {
       Block block = blockRepository.findById(blockId)
               .orElseThrow(() -> new EntityNotFoundException("해당 블럭이 없습니다."));
       return block.getProject().getId();
    }

}
