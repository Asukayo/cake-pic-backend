package com.sharkycake.proofing.enums;

/**
 * 选片单的业务状态。
 * <p>DRAFT → SELECTING → CONFIRMED → DELIVERED；
 * 任一阶段都可以关闭为 CLOSED。</p>
 */
public enum ProjectStatus {

    DRAFT,
    SELECTING,
    CONFIRMED,
    DELIVERED,
    CLOSED

}
