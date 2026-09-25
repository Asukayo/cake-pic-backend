package com.sharkycake.proofing.service;

import com.sharkycake.proofing.entity.ProofingAsset;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.proofing.vo.ProofingAssetAccessVO;
import com.sharkycake.user.entity.User;

/**
* @author shark
* @description 针对表【proofing_asset】的数据库操作Service
* @createDate 2026-09-25 09:33:36
*/
public interface ProofingAssetService extends IService<ProofingAsset> {

    /** 校验员工权限与预览图归属后，签发短时访问地址。 */
    ProofingAssetAccessVO signEmployeePreview(Long projectId, Long assetId, User loginUser);
}
