package com.sharkycake.proofing.vo;

import com.sharkycake.proofing.dto.ProofingManifest;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/** 确认时固定的照片清单，不包含内部文件地址。 */
@Data
public class ProofingSubmissionVO implements Serializable {

    private String submissionId;
    private Date confirmedAt;
    private List<ProofingManifest.Item> items;
}
