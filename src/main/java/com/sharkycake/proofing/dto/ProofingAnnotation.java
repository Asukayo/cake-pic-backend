package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;

/** 一张已选照片的一条文字意见和可选矩形。 */
@Data
public class ProofingAnnotation implements Serializable {

    private String text;

    private Rect rect;

    /** 相对服务端预览图的 0～1 坐标。 */
    @Data
    public static class Rect {
        private Double x;
        private Double y;
        private Double w;
        private Double h;
    }
}
