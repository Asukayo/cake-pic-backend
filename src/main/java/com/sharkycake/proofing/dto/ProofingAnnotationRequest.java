package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/** annotation=null 表示清空；写入时必须检查 expectedVersion。 */
@Data
public class ProofingAnnotationRequest implements Serializable {

    private ProofingAnnotation annotation;

    private Long expectedVersion;
}
