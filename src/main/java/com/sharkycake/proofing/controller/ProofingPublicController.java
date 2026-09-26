package com.sharkycake.proofing.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.proofing.dto.ProofIngProjectSessionRequest;
import com.sharkycake.proofing.dto.ProofingProjectSelectRequest;
import com.sharkycake.proofing.service.ProofingProjectService;
import com.sharkycake.proofing.service.ProofingPublicReadService;
import com.sharkycake.proofing.vo.*;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/proofing/public")
public class ProofingPublicController {

    private final ProofingProjectService proofingProjectService;
    private final ProofingPublicReadService proofingPublicReadService;

    public ProofingPublicController(ProofingProjectService proofingProjectService,
                                    ProofingPublicReadService proofingPublicReadService) {
        this.proofingProjectService = proofingProjectService;
        this.proofingPublicReadService = proofingPublicReadService;
    }
    // 就是用分享链接里的令牌，领取一个只在短时间内有效的客户访问凭证；它不是客户登录
    @PostMapping("/session")
    public BaseResponse<ProofingUserSessionVO> createSession(
            @RequestBody ProofIngProjectSessionRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingProjectService.createSessionToken(request));
    }

    /** 客户读取当前会话对应的选片单；不接受客户端提供项目 ID。 */
    @GetMapping("/project")
    public BaseResponse<ProofingPublicProjectVO> getProject(
            @RequestHeader(value = "X-Proofing-Session", required = false) String sessionToken,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingPublicReadService.getProject(sessionToken));
    }

    /** 客户按稳定顺序分页查看当前选片单的照片。 */
    @GetMapping("/items")
    public BaseResponse<Page<ProofingPublicItemVO>> listItems(
            @RequestHeader(value = "X-Proofing-Session", required = false) String sessionToken,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingPublicReadService.listItems(sessionToken, page, pageSize));
    }

    /** 当前页的预览地址过期后，客户可为该照片重新申请短时地址。 */
    @PostMapping("/assets/{assetId}/access")
    public BaseResponse<ProofingAssetAccessVO> accessPreview(
            @RequestHeader(value = "X-Proofing-Session", required = false) String sessionToken,
            @PathVariable("assetId") Long assetId,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingPublicReadService.signPreview(sessionToken, assetId));
    }

    /** 客户明确设置选择状态，并提交所看到的项目版本。 */
    @PutMapping("/items/{itemId}/selection")
    public BaseResponse<ProofingSelectVO> selection(
            @RequestHeader(value = "X-Proofing-Session", required = false) String sessionToken,
            @PathVariable("itemId") Long itemId,
            @RequestBody ProofingProjectSelectRequest selectRequest,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingPublicReadService.selectItem(sessionToken, selectRequest, itemId));
    }

}
