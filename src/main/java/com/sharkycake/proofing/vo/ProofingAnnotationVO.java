package com.sharkycake.proofing.vo;

import com.sharkycake.proofing.dto.ProofingAnnotation;
import lombok.Data;

import java.io.Serializable;


@Data
public class ProofingAnnotationVO implements Serializable {

    private ProofingAnnotation annotation; // 服务端实际保存的内容；清空时为 null


    private Long currentVersion;
}
