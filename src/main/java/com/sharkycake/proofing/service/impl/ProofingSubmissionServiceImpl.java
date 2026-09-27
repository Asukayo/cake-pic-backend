package com.sharkycake.proofing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.dto.ProofingManifest;
import com.sharkycake.proofing.entity.ProofingSubmission;
import com.sharkycake.proofing.mapper.ProofingSubmissionMapper;
import com.sharkycake.proofing.service.ProofingSubmissionService;
import com.sharkycake.proofing.vo.ProofingSubmissionVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Service;

@Service
public class ProofingSubmissionServiceImpl
        extends ServiceImpl<ProofingSubmissionMapper, ProofingSubmission>
        implements ProofingSubmissionService {

    private final ProofingProjectAuthService projectAuthService;
    private final ObjectMapper objectMapper;

    public ProofingSubmissionServiceImpl(ProofingProjectAuthService projectAuthService,
                                         ObjectMapper objectMapper) {
        this.projectAuthService = projectAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public ProofingSubmissionVO getSnapshot(Long projectId) {
        ProofingSubmission submission = lambdaQuery()
                .eq(ProofingSubmission::getProjectId, projectId)
                .one();
        ThrowUtils.throwIf(submission == null, new BusinessException(40902, "选单尚未确认"));

        ProofingManifest manifest;
        try {
            manifest = objectMapper.readValue(submission.getManifestJson(), ProofingManifest.class);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "确认清单无法解析");
        }
        ThrowUtils.throwIf(manifest == null || manifest.getItems() == null,
                ErrorCode.SYSTEM_ERROR, "确认清单不可读取");

        ProofingSubmissionVO vo = new ProofingSubmissionVO();
        vo.setSubmissionId(String.valueOf(submission.getId()));
        vo.setConfirmedAt(submission.getConfirmedAt());
        vo.setItems(manifest.getItems());
        return vo;
    }

    @Override
    public ProofingSubmissionVO getAuthorizedSnapshot(Long projectId, User loginUser) {
        projectAuthService.requireProjectPermission(
                projectId, loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);
        return getSnapshot(projectId);
    }
}
