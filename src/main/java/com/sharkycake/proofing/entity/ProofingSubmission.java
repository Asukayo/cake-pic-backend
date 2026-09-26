package com.sharkycake.proofing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/** 一次确认产生一份不可变清单。 */
@Data
@TableName("proofing_submission")
public class ProofingSubmission implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("projectId")
    private Long projectId;

    @TableField("requestId")
    private String requestId;

    /** 由确认事务生成；数据库 JSON 列由 JDBC 按字符串读写。 */
    @TableField("manifestJson")
    private String manifestJson;

    @TableField("confirmedAt")
    private Date confirmedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
