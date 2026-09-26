package com.sharkycake.proofing.controller;

import com.sharkycake.common.BaseResponse;
import com.sharkycake.common.ResultUtils;
import com.sharkycake.proofing.dto.ProofIngProjectSessionRequest;
import com.sharkycake.proofing.service.ProofingProjectService;
import com.sharkycake.proofing.vo.ProofingUserSessionVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/proofing/public")
public class ProofingPublicController {

    private final ProofingProjectService proofingProjectService;

    public ProofingPublicController(ProofingProjectService proofingProjectService) {
        this.proofingProjectService = proofingProjectService;
    }
    // 就是用分享链接里的令牌，领取一个只在短时间内有效的客户访问凭证；它不是客户登录
    @PostMapping("/session")
    public BaseResponse<ProofingUserSessionVO> createSession(
            @RequestBody ProofIngProjectSessionRequest request,
            HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return ResultUtils.success(proofingProjectService.createSessionToken(request));
    }
}