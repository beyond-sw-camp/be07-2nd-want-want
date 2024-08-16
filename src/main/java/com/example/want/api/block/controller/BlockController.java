package com.example.want.api.block.controller;

import com.example.want.api.block.domain.Block;
import com.example.want.api.block.domain.Category;
import com.example.want.api.block.dto.*;
import com.example.want.api.block.service.BlockService;
import com.example.want.api.heart.dto.HeartListResDto;
import com.example.want.api.member.login.UserInfo;
import com.example.want.common.CommonResDto;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class BlockController {
    private final BlockService blockService;

    @Operation(summary = "블록 초기 생성(완료)")
    @PostMapping("/block/create")
    public ResponseEntity<Object> createBlock(@AuthenticationPrincipal UserInfo userInfo, @RequestBody CreateBlockRqDto request) {
        Block block = blockService.createBlock(request, userInfo);
        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "Success", block.getId()), HttpStatus.OK);
    }

    @Operation(summary = "블록 활성화 안된 리스트(완료, 근데 카테고리가 없어서 이거 못쓸듯)")
    @GetMapping("/project/{projectId}/not/active/block/list")
    public ResponseEntity<Object> getNotActiveBlockList(@PathVariable Long projectId , @RequestParam(required = false) Category category, @AuthenticationPrincipal UserInfo userInfo) {
        List<BlockActiveListRsDto> blockList = blockService.getNotActiveBlockList(projectId, userInfo.getEmail(),category);
        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "Success", blockList), HttpStatus.OK);
    }

    @Operation(summary = "블록 활성화된 리스트(완료) 이것도 날짜가 없는데 되려나?")
    @GetMapping("/project/{projectId}/active/block/list")
    public ResponseEntity<Object> getBlockList(@PathVariable Long projectId , @AuthenticationPrincipal UserInfo userInfo , @PageableDefault(size = 10) Pageable pageable) {
        Page<BlockActiveListRsDto> blockList = blockService.getActiveBlockList(projectId, userInfo.getEmail(), pageable);
        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "Success", blockList), HttpStatus.OK);
    }

    @Operation(summary = "블록 상세조회 (완료)")
    @GetMapping("/block/{id}/detail")
    public ResponseEntity<Object> getBlock(@PathVariable Long id, @AuthenticationPrincipal UserInfo userInfo) {
        BlockDetailRsDto block = blockService.getBlockDetail(id, userInfo.getEmail());
        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "Success", block), HttpStatus.OK);
    }

    // 좋아요수 증가
    @Operation(summary = "블록 좋아요 추가 (완)")
    @PostMapping("/block/{blockId}/heart")
    public ResponseEntity<Object> addLikeToPost(@PathVariable Long blockId, @AuthenticationPrincipal UserInfo userInfo) {
        Block block = blockService.addLikeToPost(blockId, userInfo.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", block.getHeartCount());
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @Operation(summary = "블록 좋아요 조회 (완)")
    @GetMapping("/block/{blockId}/hearts")
    public ResponseEntity<Object> getHeartCount(@PathVariable Long blockId) {
        Long heartCount = blockService.getLikesCount(blockId);
        return new ResponseEntity<>(new CommonResDto(HttpStatus.OK, "Success", heartCount), HttpStatus.OK);
    }

    // 좋아요 수를 내림차순으로 조회 (활성화된 블록 기준, 인기 순)
    @Operation(summary = "블록 좋아요 수 내림차순 조회 - 활성화된 블록기준 인기순_수정")
    @GetMapping("/project/{projectId}/block/popular")
    public ResponseEntity<?> popularBlocks(@PathVariable Long projectId, @PageableDefault(size = 10) Pageable pageable) {
        Page<BlockActiveListRsDto> heartList = blockService.activeBlocksByPopular(projectId, pageable);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", heartList);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    // 날짜별 Block 조회
    @Operation(summary = "날짜별 블록 조회(완)")
    @GetMapping("/project/{projectId}/block/list/date")
    public ResponseEntity<?> getBlocksByDate(@PathVariable Long projectId,
                                             @Schema(description = "날짜", example = "2024-08-01") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @AuthenticationPrincipal UserInfo userInfo) {
        List<BlockActiveListRsDto> blocks = blockService.getBlocksByDate(projectId, date, userInfo.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", blocks);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    // Block 일정 등록 -> 끌어다놓기
    @Operation(summary = "블록 달력에 끌어다 놓기 추가 (완)")
    @PatchMapping("/block/addDate")
    public ResponseEntity<CommonResDto> addDateBlock(@RequestBody AddDateBlockRqDto setBlockRqDto, @AuthenticationPrincipal UserInfo userInfo) {
        BlockDetailRsDto updatedBlock = blockService.addDateBlock(setBlockRqDto, userInfo.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", updatedBlock);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }


    @Operation(summary = "블록 달력에서 목록으로 끌어다 놓기-비활성화(완)")
    @PatchMapping("/block/{blockId}/not/active")
    public ResponseEntity<Object> notActiveBlock(@PathVariable Long blockId, @AuthenticationPrincipal UserInfo userInfo) {
        BlockDetailRsDto updatedBlock = blockService.notActiveBlock(blockId, userInfo.getEmail());
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", updatedBlock);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    // 카테고리 별로 Block 조회
    @Operation(summary = "카테고리 별로 블록 조회(완)")
    @GetMapping("project/{projectId}/block/category/list")
    public ResponseEntity<?> getBlocksByCategory(@PathVariable Long projectId, @RequestParam Category category , @PageableDefault(size = 10) Pageable pageable ,@AuthenticationPrincipal UserInfo userInfo ) {
        Page<BlockActiveListRsDto> blocks = blockService.getBlocksByCategory(projectId, category, userInfo.getEmail() ,pageable);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", blocks);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

//    블록들 업데이트
    @Operation(summary = "블록 업데이트-프론트 입맛대로, 넣고싶은거맛넣고 나머진 다지우기 (완)")
    @PatchMapping("/block/{id}/update")
    public ResponseEntity<Object> updateBlock(@PathVariable Long id, @RequestBody UpdateBlockRqDto request, @AuthenticationPrincipal UserInfo userInfo) {
        BlockDetailRsDto blockDetailRsDto = blockService.updateBlock(id, request, userInfo);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", blockDetailRsDto);
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<CommonResDto> deleteBlock(@AuthenticationPrincipal UserInfo userInfo, @PathVariable Long id) {
        Block deletedBlock = blockService.blockDelete(userInfo, id);
        CommonResDto commonResDto = new CommonResDto(HttpStatus.OK, "Success", deletedBlock.getIsDeleted());
        return new ResponseEntity<>(commonResDto, HttpStatus.OK);
    }
}
