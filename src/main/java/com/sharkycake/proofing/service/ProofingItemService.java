package com.sharkycake.proofing.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.proofing.entity.ProofingItem;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sharkycake.proofing.vo.ProofingItemVO;
import com.sharkycake.user.entity.User;

/**
* @author shark
* @description 针对表【proofing_item】的数据库操作Service
* @createDate 2026-09-25 09:39:07
*/
public interface ProofingItemService extends IService<ProofingItem> {

    /** 按项目分页查询员工可见的预览明细。 */
    Page<ProofingItemVO> listProjectItems(Long projectId, long page, long pageSize, User loginUser);
}
