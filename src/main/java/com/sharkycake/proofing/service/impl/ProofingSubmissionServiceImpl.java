package com.sharkycake.proofing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.proofing.entity.ProofingSubmission;
import com.sharkycake.proofing.mapper.ProofingSubmissionMapper;
import com.sharkycake.proofing.service.ProofingSubmissionService;
import org.springframework.stereotype.Service;

@Service
public class ProofingSubmissionServiceImpl
        extends ServiceImpl<ProofingSubmissionMapper, ProofingSubmission>
        implements ProofingSubmissionService {
}
