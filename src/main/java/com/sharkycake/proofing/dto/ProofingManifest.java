package com.sharkycake.proofing.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 确认时从数据库构造，按 sortOrder、id 排好的固定已选清单。 */
@Data
public class ProofingManifest implements Serializable {

    private List<Item> items;

    @Data
    public static class Item {
        private String itemId;
        private String displayName;
        private String previewAssetId;
        private Integer previewWidth;
        private Integer previewHeight;
        private ProofingAnnotation annotation;
    }
}
