package com.sharkycake.proofing.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.service.ProofingItemService;
import com.sharkycake.proofing.mapper.ProofingItemMapper;
import com.sharkycake.proofing.vo.ProofingItemVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
* @author shark
* @description 针对表【proofing_item】的数据库操作Service实现
* @createDate 2026-09-25 09:39:07
*/
@Service
public class ProofingItemServiceImpl extends ServiceImpl<ProofingItemMapper, ProofingItem>
    implements ProofingItemService{

    private final ProofingProjectAuthService projectAuthService;

    public ProofingItemServiceImpl(ProofingProjectAuthService projectAuthService) {
        this.projectAuthService = projectAuthService;
    }

    @Override
    public Page<ProofingItemVO> listProjectItems(Long projectId, long page, long pageSize, User loginUser) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0 || page < 1 || pageSize < 1 || pageSize > 50,
                ErrorCode.PARAMS_ERROR, "分页或项目 ID 不合法");
        // 只使用项目 ID 反查真实空间归属，不接受请求方提供的 spaceId。
        projectAuthService.requireProjectPermission(
                projectId, loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);

        Page<ProofingItem> itemPage = this.lambdaQuery()
                .eq(ProofingItem::getProjectId, projectId)
                .orderByAsc(ProofingItem::getSortOrder, ProofingItem::getId)
                .page(new Page<>(page, pageSize));

        Page<ProofingItemVO> result = new Page<>(
                itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        // 不把 Entity、私有桶或对象 Key 直接返回给前端。
        result.setRecords(itemPage.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return result;
    }

    private ProofingItemVO toVo(ProofingItem item) {
        ProofingItemVO vo = new ProofingItemVO();
        vo.setItemId(String.valueOf(item.getId()));
        vo.setPreviewAssetId(String.valueOf(item.getPreviewAssetId()));
        vo.setDisplayName(item.getDisplayName());
        vo.setSortOrder(item.getSortOrder());
        return vo;
    }
}




