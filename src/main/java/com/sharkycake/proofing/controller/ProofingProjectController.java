package com.sharkycake.proofing.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.proofing.dto.ProofingProjectCloseRequest;
import com.sharkycake.proofing.dto.ProofingProjectCreateRequest;
import com.sharkycake.proofing.dto.ProofingProjectPublishRequest;
import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import com.sharkycake.proofing.dto.ProofingProjectUpdateRequest;
import com.sharkycake.proofing.service.ProofingAssetService;
import com.sharkycake.proofing.service.ProofingItemService;
import com.sharkycake.proofing.service.ProofingProjectService;
import com.sharkycake.proofing.upload.ProofingPreviewUploadService;
import com.sharkycake.proofing.vo.ProofingAssetAccessVO;
import com.sharkycake.proofing.vo.ProofingItemVO;
import com.sharkycake.proofing.vo.ProofingPreviewUploadVO;
import com.sharkycake.proofing.vo.ProofingProjectVO;
import com.sharkycake.user.entity.User;
import com.sharkycake.user.service.UserService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 选片单的创建、查询、修改和关闭接口。
 */
@RestController
@RequestMapping("/proofing/projects")
public class ProofingProjectController {

    private final ProofingProjectService proofingProjectService;
    private final ProofingAssetService proofingAssetService;
    private final ProofingItemService proofingItemService;
    private final ProofingPreviewUploadService previewUploadService;
    private final UserService userService;

    public ProofingProjectController(ProofingProjectService proofingProjectService,
                                    ProofingAssetService proofingAssetService,
                                    ProofingItemService proofingItemService,
                                    ProofingPreviewUploadService previewUploadService,
                                    UserService userService) {
        this.proofingProjectService = proofingProjectService;
        this.proofingAssetService = proofingAssetService;
        this.proofingItemService = proofingItemService;
        this.previewUploadService = previewUploadService;
        this.userService = userService;
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

    /**
     * 用于展示空间内选单项目，目前支持status查询
     */
    @GetMapping
    public BaseResponse<Page<ProofingProjectVO>> list(
            @ModelAttribute ProofingProjectQueryRequest queryRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.listProjects(queryRequest, httpServletRequest));
    }

    /**
     * 根据对性的选单id，查询特定的选单
     */
    @GetMapping("/{id}")
    public BaseResponse<ProofingProjectVO> get(
            @PathVariable Long id, HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.getProject(id, httpServletRequest));
    }

    /**
     * 根据id进行更新操作，支持三个属性
     */
    @PatchMapping("/{id}")
    public BaseResponse<ProofingProjectVO> update(
            @PathVariable Long id,
            @RequestBody ProofingProjectUpdateRequest updateRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.updateDraft(id, updateRequest, httpServletRequest));
    }

    /**
     * 上传单张预览；前端逐张上传，并将响应中的 version 用于下一张。
     */
    @PostMapping(value = "/{id}/previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<ProofingPreviewUploadVO> uploadPreview(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam("expectedVersion") Long expectedVersion,
            HttpServletRequest httpServletRequest) throws Exception {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(previewUploadService.uploadPreview(
                id, file, expectedVersion, loginUser));
    }

    /**
     * 员工分页查看选片单中的预览明细；图片内容通过单独的签名访问接口获取。
     */
    @GetMapping("/{id}/items")
    public BaseResponse<Page<ProofingItemVO>> listItems(
            @PathVariable("id") Long id,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "10") long pageSize,
            HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        return ResultUtils.success(proofingItemService.listProjectItems(id, page, pageSize, loginUser));
    }

    /**
     * 员工申请单张预览图的短时 URL；资产归属和 READY 状态由 Service 校验。
     */
    @PostMapping("/{id}/assets/{assetId}/access")
    public BaseResponse<ProofingAssetAccessVO> accessPreview(
            @PathVariable("id") Long id,
            @PathVariable("assetId") Long assetId,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 签名 URL 是短期访问凭证，不让浏览器或代理缓存响应。
        httpServletResponse.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingAssetService.signEmployeePreview(id, assetId, loginUser));
    }

    /** 移除草稿中的一张图片，前端用返回的版本发起下一次修改。 */
    @DeleteMapping("/{id}/items/{itemId}")
    public BaseResponse<Long> removeDraftItem(
            @PathVariable("id") Long id,
            @PathVariable("itemId") Long itemId,
            @RequestParam("expectedVersion") Long expectedVersion,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.removeDraftItem(
                id, itemId, expectedVersion, httpServletRequest));
    }

    /** 发布草稿选单，返回状态和版本更新后的项目信息。 */
    @PostMapping("/{id}/publish")
    public BaseResponse<ProofingProjectVO> publish(
            @PathVariable("id") Long id,
            @RequestBody ProofingProjectPublishRequest publishRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.publish(
                id, publishRequest, httpServletRequest));
    }

    /**
     * 关闭选单
     */
    @PostMapping("/{id}/close")
    public BaseResponse<ProofingProjectVO> close(
            @PathVariable Long id,
            @RequestBody ProofingProjectCloseRequest closeRequest,
            HttpServletRequest httpServletRequest) {
        return ResultUtils.success(proofingProjectService.closeProject(id, closeRequest, httpServletRequest));
    }

}
