package com.sharkycake.proofing.dto;


import lombok.Data;

import java.io.Serializable;

@Data
public class ProofIngProjectSessionRequest implements Serializable {


    public String publicId;

    public String shareToken;

}
