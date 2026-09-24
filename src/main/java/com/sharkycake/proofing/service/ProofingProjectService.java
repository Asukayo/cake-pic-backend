package com.sharkycake.proofing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.proofing.dto.ProofingProjectCloseRequest;
import com.sharkycake.proofing.dto.ProofingProjectCreateRequest;
import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import com.sharkycake.proofing.dto.ProofingProjectUpdateRequest;
import com.sharkycake.proofing.entity.ProofingProject;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.proofing.vo.ProofingProjectVO;

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

    ProofingProjectVO closeProject(Long projectId, ProofingProjectCloseRequest closeRequest,
                                    HttpServletRequest httpServletRequest);
}
