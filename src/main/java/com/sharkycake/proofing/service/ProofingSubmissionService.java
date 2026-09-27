package com.sharkycake.proofing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.proofing.entity.ProofingSubmission;
import com.sharkycake.proofing.vo.ProofingSubmissionVO;
import com.sharkycake.user.entity.User;

/** 供确认事务及确认后的只读接口使用。 */
public interface ProofingSubmissionService extends IService<ProofingSubmission> {

    /** 调用方已完成客户会话校验时，按项目读取固定清单。 */
    ProofingSubmissionVO getSnapshot(Long projectId);

    /** 员工读取固定清单前检查项目所在空间的查看权限。 */
    ProofingSubmissionVO getAuthorizedSnapshot(Long projectId, User loginUser);
}
