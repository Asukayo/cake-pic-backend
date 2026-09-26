package com.sharkycake.proofing.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ProofingUserSessionVO implements Serializable {

    public String token;

    String projectId;


    Date expiresAt;
}
