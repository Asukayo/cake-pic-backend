package com.sharkycake.proofing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.proofing.dto.*;
import com.sharkycake.proofing.entity.ProofingProject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.proofing.vo.ProofingProjectVO;
import com.sharkycake.proofing.vo.ProofingSharedVO;
import com.sharkycake.proofing.vo.ProofingUserSessionVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author shark
* @description 针对表【proofing_project】的数据库操作Service
* @createDate 2026-09-24 16:07:53
*/
public interface ProofingProjectService extends IService<ProofingProject> {

    ProofingProjectVO create(ProofingProjectCreateRequest proofingProjectCreateRequest,
                             HttpServletRequest httpServletRequest) throws Exception;

    Page<ProofingProjectVO> listProjects(ProofingProjectQueryRequest queryRequest,
                                         HttpServletRequest httpServletRequest);

    ProofingProjectVO getProject(Long projectId, HttpServletRequest httpServletRequest);

    ProofingProjectVO updateDraft(Long projectId, ProofingProjectUpdateRequest updateRequest,
                                   HttpServletRequest httpServletRequest);

    /** 从草稿中移除一张预览图，返回更新后的项目版本。 */
    Long removeDraftItem(Long projectId, Long itemId, Long expectedVersion,
                         HttpServletRequest httpServletRequest);

    /** 校验图片数量后发布草稿选单。 */
    ProofingProjectVO publish(Long projectId, ProofingProjectPublishRequest publishRequest,
                              HttpServletRequest httpServletRequest);

    ProofingProjectVO closeProject(Long projectId, ProofingProjectCloseRequest closeRequest,
                                    HttpServletRequest httpServletRequest);

    /**
     * 创建临时短链
     */
    ProofingSharedVO createSharingLink(Long projectId, HttpServletRequest httpServletRequest);

    Boolean revokeSharing(Long projectId, HttpServletRequest httpServletRequest);

    ProofingUserSessionVO createSessionToken(ProofIngProjectSessionRequest request);
}
