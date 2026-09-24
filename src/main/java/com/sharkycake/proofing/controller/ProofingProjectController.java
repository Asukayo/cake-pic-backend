package com.sharkycake.proofing.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.proofing.dto.ProofingProjectCloseRequest;
import com.sharkycake.proofing.dto.ProofingProjectCreateRequest;
import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import com.sharkycake.proofing.dto.ProofingProjectUpdateRequest;
import com.sharkycake.proofing.service.ProofingProjectService;
import com.sharkycake.proofing.vo.ProofingProjectVO;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 选片单的创建、查询、修改和关闭接口。
 */
@RestController
@RequestMapping("/proofing/projects")
public class ProofingProjectController {

    private final ProofingProjectService proofingProjectService;

    public ProofingProjectController(ProofingProjectService proofingProjectService) {
        this.proofingProjectService = proofingProjectService;
    }

    /**
     * 进行选单创建方法
     */
    @PostMapping()
    public BaseResponse<ProofingProjectVO> create(
            @RequestBody ProofingProjectCreateRequest proofingProjectCreateRequest,
            HttpServletRequest httpServletRequest) throws Exception {
        return ResultUtils.success(
                proofingProjectService.create(
                        proofingProjectCreateRequest,
                        httpServletRequest
                )
        )
         ;
    }

    @GetMapping
    public BaseResponse<Page<ProofingProjectVO>> list(
            @ModelAttribute ProofingProjectQueryRequest queryRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.listProjects(queryRequest, httpServletRequest));
    }

    @GetMapping("/{id}")
    public BaseResponse<ProofingProjectVO> get(
            @PathVariable Long id, HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.getProject(id, httpServletRequest));
    }

    @PatchMapping("/{id}")
    public BaseResponse<ProofingProjectVO> update(
            @PathVariable Long id,
            @RequestBody ProofingProjectUpdateRequest updateRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.updateDraft(id, updateRequest, httpServletRequest));
    }

    @PostMapping("/{id}/close")
    public BaseResponse<ProofingProjectVO> close(
            @PathVariable Long id,
            @RequestBody ProofingProjectCloseRequest closeRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.closeProject(id, closeRequest, httpServletRequest));
    }

}
