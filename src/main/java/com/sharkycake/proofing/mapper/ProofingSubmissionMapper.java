package com.sharkycake.proofing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sharkycake.proofing.entity.ProofingSubmission;

/** 确认清单只在确认事务中插入，此处不提供公开写接口。 */
public interface ProofingSubmissionMapper extends BaseMapper<ProofingSubmission> {
}
